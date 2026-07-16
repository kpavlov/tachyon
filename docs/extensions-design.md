# MCP Protocol Extensions — Design

## Overview

Extensions are pluggable components that servers register at build time. They participate in the MCP capability negotiation handshake, install method handlers, and react to connection lifecycle events.

---

## MCP Protocol Requirements

The `extensions` capability appears in the MCP spec from `2025-06-18` onward. Tachyon currently negotiates `2025-11-25` (see `InitializeHandler.negotiatedVersion`); the extensions field is carried in that version's `capabilities` object as well.

Both sides advertise extensions in their `capabilities.extensions` map during `initialize`:

```json
// Client capabilities (initialize request)
"capabilities": {
  "extensions": {
    "io.modelcontextprotocol/ui": { "mimeTypes": ["text/html;profile=mcp-app"] }
  }
}

// Server capabilities (initialize response)
"capabilities": {
  "extensions": {
    "io.modelcontextprotocol/ui": {}
  }
}
```

Each extension key is a URI-style ID; the value is a settings object (empty object `{}` = no settings).

**Graceful degradation rule**: if one side supports an extension the other does not, the supporting side MUST fall back to core protocol behavior OR reject the connection if the extension is mandatory.

**Negotiation result**: the intersection of client-declared and server-declared extension IDs is the set of *enabled* extensions for that connection.

---

## Extension Lifecycle

```
Server startup
    │
    ▼
bootstrap(McpServer)          ← install method handlers, tools, resources
    │
    ▼  (per connection)
onConnectionInit(McpContext, Map<String,JsonNode> clientSettings)
                              ← called only for negotiated extensions
    │
    ▼  (per connection)
onConnectionClose(McpContext) ← cleanup per-connection state
    │
    ▼  (server shutdown)
shutdown()                    ← global cleanup
```

---

## Core Interface

```java
package me.kpavlov.tachyon.mcp.server;

import java.util.Map;
import me.kpavlov.tachyon.mcp.server.session.McpContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

public interface McpExtension {

    /** Unique URI-style identifier, e.g. "io.modelcontextprotocol/ui". */
    String extensionId();

    /**
     * Settings schema advertised in ServerCapabilities.extensions.
     * Return JsonNodeFactory.instance.objectNode() for no settings.
     */
    default JsonNode serverSettings() {
        return JsonNodeFactory.instance.objectNode();
    }

    /** JSON-RPC methods owned by this extension. The core dispatcher uses this
     *  to enforce negotiation + _meta gating before routing. */
    default Set<String> methods() {
        return Set.of();
    }

    /** If true, calls to this extension's {@link #methods()} must carry
     *  _meta.&lt;extensionId&gt;; the dispatcher rejects them with -32602 otherwise. */
    default boolean requiresMetaEnvelope() {
        return true;
    }

    /**
     * Called once at server startup. Install method handlers, tools, resources here.
     * May be called before any connection exists.
     */
    default void bootstrap(McpServer server) {}

    /**
     * Called after capability negotiation succeeds for this extension and connection.
     * Only invoked when both client and server declared this extension.
     *
     * @param clientSettings the settings object the client provided for this extension
     */
    default void onConnectionInit(McpContext context, Map<String, JsonNode> clientSettings) {}

    /**
     * Called when the connection closes (normal or abnormal).
     * Only invoked if onConnectionInit was called for this context.
     */
    default void onConnectionClose(McpContext context) {}

    /** Called when the server shuts down. Release global resources here. */
    default void shutdown() {}
}
```

---

## Builder Integration

The builder gains an `ExtensionsStep` between `FeatureStep` and `SessionStep`:

```java
TachyonServer.builder()
    .tool(new MyToolHandler())
    .extension(new UiExtension())   // → ExtensionsStep
    .extension(new AuthExtension())
    .host("localhost").port(8080)
    .start();
```

`BuilderState` gains `List<McpExtension> extensions = new ArrayList<>()`.

---

## McpContext Additions

`McpContext` gains two capabilities needed by extensions:

### 1. Extension negotiation query

```java
boolean isExtensionEnabled(String extensionId);
```

Backed by the set of negotiated extensions stored in `McpSession`. The set is populated during `initialize` handling in `InitializeHandler`.

### 2. User attributes

Type-safe, per-connection attributes for extension state:

```java
<T> void setAttribute(AttributeKey<T> key, T value);
<T> @Nullable T getAttribute(AttributeKey<T> key);
```

`AttributeKey<T>` follows Netty's pattern: `static final AttributeKey<MyState> KEY = AttributeKey.valueOf("my-ext/state")`. Store the map in `McpSession` as `ConcurrentHashMap<AttributeKey<?>, Object>`.

---

## McpSession Additions

```java
// Store negotiated extension IDs
private final Set<String> enabledExtensions = ConcurrentHashMap.newKeySet();

public void enableExtension(String extensionId) {
    enabledExtensions.add(extensionId);
}

public boolean isExtensionEnabled(String extensionId) {
    return enabledExtensions.contains(extensionId);
}

// User attributes
private final ConcurrentHashMap<AttributeKey<?>, Object> attributes = new ConcurrentHashMap<>();

@SuppressWarnings("unchecked")
public <T> void setAttribute(AttributeKey<T> key, T value) {
    attributes.put(key, value);
}

@SuppressWarnings("unchecked")
public <T> @Nullable T getAttribute(AttributeKey<T> key) {
    return (T) attributes.get(key);
}
```

---

## Capability Negotiation in InitializeHandler

```java
// 1. Get client-declared extensions from InitializeRequestParams
var clientExtensions = params.capabilities() != null
    ? params.capabilities().extensions()  // Map<String, JsonNode>
    : Map.of();

// 2. Get server-registered extensions
var serverExtensions = server.extensions(); // List<McpExtension>

// 3. Negotiate: intersection
for (var ext : serverExtensions) {
    if (clientExtensions.containsKey(ext.extensionId())) {
        context.session().enableExtension(ext.extensionId());
        var clientSettings = clientExtensions.get(ext.extensionId());
        ext.onConnectionInit(context, asMap(clientSettings));
    }
}

// 4. Include enabled extensions in InitializeResult capabilities
var negotiatedExtensions = serverExtensions.stream()
    .filter(e -> context.session().isExtensionEnabled(e.extensionId()))
    .collect(toMap(McpExtension::extensionId, McpExtension::serverSettings));
// Set in resultCapabilities.extensions
```

---

## McpServer Additions

```java
private final List<McpExtension> extensions; // set from BuilderState

// Called from constructor after registering defaults
private void bootstrapExtensions() {
    for (var ext : extensions) {
        ext.bootstrap(this);
    }
}

// Called from close()
private void shutdownExtensions() {
    for (var ext : extensions) {
        try { ext.shutdown(); } catch (Exception e) { logger.warn(...); }
    }
}

public List<McpExtension> extensions() {
    return Collections.unmodifiableList(extensions);
}
```

`McpSession.close()` (or the disconnect path in `McpEndpointHandler`) calls `onConnectionClose` for each enabled extension.

---

## Method Dispatch for Extension Handlers

Extensions own a set of JSON-RPC methods. They declare both the methods and (optionally) whether each call must carry an `_meta.<extensionId>` envelope:

```java
// McpExtension additions:
default Set<String> methods() { return Set.of(); }

/** If true, the core dispatcher rejects calls to this extension's methods
 *  that do not carry _meta.<extensionId>. */
default boolean requiresMetaEnvelope() { return true; }
```

During `bootstrap`, the server records `method → extensionId` ownership. The **core dispatcher** (`JsonRpcDispatcher`), not the individual handler, enforces routing rules before invoking an extension handler:

1. **Method ownership** — look up the owning extension for the method. If none, dispatch normally (core method).
2. **Negotiation check** — if the owning extension is not enabled for this connection (`session.isExtensionEnabled(extensionId)` is false), reject with `-32601` (Method not found) so an un-negotiated extension is invisible to the client.
3. **`_meta/<extension>` check** — if `requiresMetaEnvelope()` is true and `params._meta.<extensionId>` is absent, reject with `-32602` (Invalid params).
4. **Dispatch** — invoke the handler with the connection's `McpContext`.

This centralizes the "verify method name and `_meta/<extension>`" gate in the dispatcher rather than duplicating it in every handler. The handler can still call `context.isExtensionEnabled(extensionId)` defensively but does not need to.

```java
// JsonRpcDispatcher, after resolving handler by method name:
var owningExtensionId = server.extensionForMethod(method); // @Nullable
if (owningExtensionId != null) {
    if (session == null || !session.isExtensionEnabled(owningExtensionId)) {
        return errorResult(id, McpErrors.METHOD_NOT_FOUND, "Method not found");
    }
    if (server.extensionRequiresMeta(owningExtensionId)
            && !hasMetaKey(params, owningExtensionId)) {
        return errorResult(id, McpErrors.INVALID_REQUEST, "Invalid request");
    }
}
```

Note: stateless connections have no negotiated extensions (no session), so extension methods are always `-32601` in stateless mode.

> 🔒 Error messages use fixed strings and never echo client-supplied input (method name, `_meta` values, extension id). Log the specific cause server-side at debug level. This matches the repo's "don't echo input parameters" convention.

---

## Netty Channel Attribute for McpContext

Store `McpContext` as a channel attribute so extension code can access it from Netty handlers:

```java
// In McpEndpointHandler:
static final AttributeKey<McpContext> MCP_CONTEXT_KEY =
    AttributeKey.valueOf("mcp-context");

// After session activation (in handlePost, after dispatchInitializeAsync):
ctx.channel().attr(MCP_CONTEXT_KEY).set(toolContext);
```

Extension code that installs Netty handlers during `bootstrap` can then read:
```java
var mcpContext = ctx.channel().attr(McpEndpointHandler.MCP_CONTEXT_KEY).get();
```

---

## ServerCapabilities and ClientCapabilities Extensions Field

Both records need an `extensions` field. Since they're `@Generated("ts2java")`:

```java
// Add to ServerCapabilities record:
@JsonProperty("extensions") @Nullable Map<String, JsonNode> extensions,

// Add to ClientCapabilities record:
@JsonProperty("extensions") @Nullable Map<String, JsonNode> extensions,
```

The `InitializeHandler` populates `extensions` in the result using the negotiated set. The builder's `InitializeHandler` factory reads from `McpServer.extensions()`.

---

## Tasks as the Reference Extension (bridging core ↔ extension)

In the MCP `2025-11-25` spec, Tasks are a **core** feature: advertised via `capabilities.tasks` and dispatched through core method handlers (`tasks/get`, `tasks/list`, `tasks/result`). In the newer draft, Tasks are being **moved out of core into an extension** (`extensionId` ≈ `io.modelcontextprotocol/tasks` — confirm against the draft before shipping).

Tachyon bridges both worlds by making the **extension the single implementation** and having the legacy core feature **delegate** to it:

```
            ┌──────────────────────────────┐
            │        TaskRegistry          │   ← single source of truth
            │  (state machine, TTL, store) │
            └──────────────┬───────────────┘
                           │ both front doors share it
            ┌──────────────┴───────────────┐
            ▼                               ▼
   core task handlers              TasksExtension
   (2025-11-25 clients)            (draft clients that negotiate
   advertised in                    "io.modelcontextprotocol/tasks";
   capabilities.tasks               advertised in capabilities.extensions)
```

Design rules:

- **`TasksExtension implements McpExtension`** owns `tasks/get`, `tasks/list`, `tasks/result`, `tasks/delete` via `methods()`, and wraps the existing `TaskRegistry`. Its `bootstrap()` registers the handlers and the TTL janitor.
- **The core task handlers delegate** to the same `TaskRegistry` (no duplicated logic). Concretely, `TaskRegistry.registerHandlers()` stays, but the handler bodies and the registry instance are shared with `TasksExtension` — the extension does not get its own registry.
- **Always-registered for compatibility.** Because 2025-11-25 clients treat tasks as core (they do *not* send a `tasks` extension in `capabilities.extensions`), the tasks feature must work without negotiation. So the tasks methods stay registered as core methods and are **exempt from the extension negotiation gate** in the dispatcher (see below). The `TasksExtension` is only what advertises tasks under `capabilities.extensions` for draft clients that opt in.
- **Dispatcher gating exemption.** The "reject un-negotiated extension methods with -32601" rule (Method Dispatch section) must NOT apply to the tasks methods, or 2025-11-25 clients would break. Either register tasks methods as core (not via `extensionForMethod`), or special-case `requiresMetaEnvelope()=false` + "core-compatible" flag on `TasksExtension`. Recommended: keep tasks methods in the **core** handler map; `TasksExtension` reuses the same registry and only contributes the `capabilities.extensions` advertisement + draft `_meta` semantics.

Net effect: one `TaskRegistry`, two advertisements (`capabilities.tasks` for 2025-11-25, `capabilities.extensions["io.modelcontextprotocol/tasks"]` for the draft), zero duplicated dispatch logic.

---

## Writing a Custom Extension

```java
public class AuditExtension implements McpExtension {

    private static final AttributeKey<AuditLog> LOG_KEY =
        AttributeKey.valueOf("audit/log");

    @Override
    public String extensionId() { return "com.example/audit"; }

    @Override
    public void bootstrap(McpServer server) {
        server.registerHandler("audit/log", (ctx, params) -> {
            if (!ctx.isExtensionEnabled(extensionId())) {
                return McpErrors.invalidRequest("audit extension not negotiated");
            }
            var log = ctx.getAttribute(LOG_KEY);
            return log != null ? log.entries() : List.of();
        });
    }

    @Override
    public void onConnectionInit(McpContext context, Map<String, JsonNode> clientSettings) {
        context.setAttribute(LOG_KEY, new AuditLog());
    }

    @Override
    public void onConnectionClose(McpContext context) {
        var log = context.getAttribute(LOG_KEY);
        if (log != null) log.flush();
    }
}
```

Register at build time:

```java
TachyonServer.builder()
    .tool(new MyTool())
    .extension(new AuditExtension())
    .host("localhost").port(8080)
    .start();
```

---

## Security Notes

- Extensions run inside the server process with full access to `McpServer`. Treat third-party extensions as trusted code.
- Mandatory extensions (reject if not negotiated) should fail fast in `onConnectionInit` — throw or call `session.close()`.
- User attributes are per-connection and cleaned up on `onConnectionClose`.
