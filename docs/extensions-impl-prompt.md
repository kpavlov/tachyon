# Agent Prompt: Implement MCP Protocol Extensions

## Goal

Implement pluggable MCP extensions for the Tachyon MCP server. Read `docs/extensions-design.md` for the complete design before touching any code. Read `docs/tasks.md` for the Tasks SEP context.

**CRITICAL CONSTRAINT**: Never run `mvn install`. Build with:
```
mvn -pl tachyon-mcp-server test-compile        # compile check
mvn -pl tachyon-mcp-server,e2e test            # full test
mvn spotless:apply                              # format before committing
```

---

## Codebase Orientation

Primary module: `tachyon-mcp-server/src/main/java/me/kpavlov/tachyon/mcp/`

Key files to read before starting:
- `server/session/McpContext.java` — interface extended by `ToolContextImpl`
- `server/session/McpSession.java` — per-connection state
- `server/McpServer.java` — central server, holds registries
- `server/handlers/InitializeHandler.java` — capability negotiation happens here
- `server/builder/FeatureStep.java` + `BuilderState.java` — builder chain
- `server/builder/ExtensionsStep.java` — does not exist yet, you will create it
- `protocol/models/ServerCapabilities.java` — `@Generated("ts2java")` record
- `protocol/models/ClientCapabilities.java` — `@Generated("ts2java")` record
- `server/netty/McpEndpointHandler.java` — Netty HTTP handler, dispatch entry point
- `server/netty/JsonRpcDispatcher.java` — routes method calls to handlers

Builder chain: `IdentityStep → CapabilitiesStep → FeatureStep → ExtensionsStep → SessionStep → NetworksStep`

---

## Implementation Steps (in order)

### 1. `McpExtension` interface

Create `tachyon-mcp-server/src/main/java/me/kpavlov/tachyon/mcp/server/McpExtension.java`:

```java
public interface McpExtension {
    String extensionId();
    default JsonNode serverSettings() { return JsonNodeFactory.instance.objectNode(); }

    /** JSON-RPC methods owned by this extension (for dispatcher gating). */
    default Set<String> methods() { return Set.of(); }

    /** If true, the core dispatcher rejects calls to methods() lacking _meta.<extensionId>. */
    default boolean requiresMetaEnvelope() { return true; }

    default void bootstrap(McpServer server) {}
    default void onConnectionInit(McpContext context, Map<String, JsonNode> clientSettings) {}
    default void onConnectionClose(McpContext context) {}
    default void shutdown() {}
}
```

### 2. `McpSession` additions

In `McpSession.java` add:
- `private final Set<String> enabledExtensions = ConcurrentHashMap.newKeySet()`
- `public void enableExtension(String id)` / `public boolean isExtensionEnabled(String id)`
- `private final ConcurrentHashMap<AttributeKey<?>, Object> attributes = new ConcurrentHashMap<>()`
- `public <T> void setAttribute(AttributeKey<T> key, T value)` / `public <T> @Nullable T getAttribute(AttributeKey<T> key)`

`AttributeKey` import: `io.netty.util.AttributeKey` (already on classpath via Netty).

### 3. `McpContext` interface additions

Add to `McpContext.java`:
```java
default boolean isExtensionEnabled(String extensionId) { return false; }
default <T> void setAttribute(AttributeKey<T> key, T value) {}
default <T> @Nullable T getAttribute(AttributeKey<T> key) { return null; }
```

Override these in `ToolContextImpl` by delegating to `session.isExtensionEnabled()` / `session.setAttribute()` / `session.getAttribute()`. Guard against null session (stateless mode: `session()` may be null — stateless mode never has enabled extensions).

### 4. `ServerCapabilities` and `ClientCapabilities` — add `extensions` field

Both records are `@Generated("ts2java")` but must be edited manually here.

In `ServerCapabilities.java`:
- Add `@JsonProperty("extensions") @Nullable Map<String, JsonNode> extensions` as the **last** constructor component
- Add `extensions(Map<String, JsonNode> extensions)` to the `Builder`

In `ClientCapabilities.java` (same pattern):
- Add `@JsonProperty("extensions") @Nullable Map<String, JsonNode> extensions`

### 5. `McpServer` additions

In `McpServer.java`:
- Add `private final List<McpExtension> extensions` field
- Add `private final Map<String, String> extensionMethodOwners = new ConcurrentHashMap<>()` (method → extensionId)
- Add `private final Map<String, McpExtension> extensionsById` (for `requiresMetaEnvelope` lookups)
- In the main constructor, accept `List<McpExtension> extensions` and call `bootstrapExtensions()` at the end
- `bootstrapExtensions()`: for each extension, register its `methods()` into `extensionMethodOwners`, index it by ID, then call `ext.bootstrap(this)`
- `public List<McpExtension> extensions()` returns unmodifiable view
- `public @Nullable String extensionForMethod(String method)` → `extensionMethodOwners.get(method)`
- `public boolean extensionRequiresMeta(String extensionId)` → looks up the extension and returns `requiresMetaEnvelope()`
- In `close()`, iterate extensions in reverse order and call `ext.shutdown()`
- Add `public void registerHandler(String method, McpMethodHandler handler)` (expose `methodHandlers.put(method, handler)`)

The simpler constructors pass `List.of()` for extensions.

### 6. `InitializeHandler` — capability negotiation

In `InitializeHandler.java`:
- Accept `List<McpExtension> extensions` in constructor
- In `handle()`:
  1. Extract `clientExtensions` from `params.capabilities().extensions()` (null-safe, default `Map.of()`)
  2. For each server extension, if `clientExtensions.containsKey(ext.extensionId())`: call `context.session().enableExtension(ext.extensionId())` then `ext.onConnectionInit(context, clientSettings)`
  3. Build `negotiatedExtensions` map (ID → `serverSettings()`) for extensions that are enabled on this session
  4. Include `negotiatedExtensions` in `resultCapabilities` as the `extensions` field

For stateless mode, `context.session()` is a dummy session — skip extension negotiation entirely (check `server.isStateless()`).

### 7. `McpEndpointHandler` — store McpContext in channel attribute

Add:
```java
static final AttributeKey<McpContext> MCP_CONTEXT_KEY = AttributeKey.valueOf("mcp-context");
```

After `dispatchInitializeAsync` resolves (in `handlePostResponse`), if `sessionId` is non-null (stateful), retrieve the session and write the context to the channel attribute.

Also, on channel disconnect (override `channelInactive`), call `onConnectionClose` for each enabled extension:
```java
var mcpContext = ctx.channel().attr(MCP_CONTEXT_KEY).getAndSet(null);
if (mcpContext != null && mcpContext.session() != null) {
    for (var ext : server.extensions()) {
        if (mcpContext.isExtensionEnabled(ext.extensionId())) {
            try { ext.onConnectionClose(mcpContext); } catch (Exception e) { logger.warn(...); }
        }
    }
}
```

### 8. `JsonRpcDispatcher` — request routing respects `_meta/<extension>`

This is the core gate that enforces "verify method name **and** `_meta/<extension>` before dispatching to an extension handler." Add it in `dispatchRequestAsync`, **after** resolving `handler = server.getHandler(method)` and **before** invoking the handler.

```java
var owningExtensionId = server.extensionForMethod(method); // @Nullable
if (owningExtensionId != null) {
    // 1. Negotiation gate: an un-negotiated extension method is invisible.
    if (session == null || !session.isExtensionEnabled(owningExtensionId)) {
        // Static message — do NOT echo the method name or extension id back.
        return errorResult(id, McpErrors.METHOD_NOT_FOUND, "Method not found");
    }
    // 2. _meta envelope gate.
    if (server.extensionRequiresMeta(owningExtensionId)
            && !hasMetaKey(params, owningExtensionId)) {
        // Static message — do NOT echo _meta contents or the method name.
        return errorResult(id, McpErrors.INVALID_REQUEST, "Invalid request");
    }
}
```

> 🔒 **Security:** error responses MUST NOT reflect raw client-supplied input — method names, `_meta` values, settings objects, task IDs, etc. Use fixed strings; log the detail server-side instead (`logger.debug`). This matches the existing repo convention (commit "don't echo input parameters"). Apply the same rule to every new error path in this work, not just these two.

Add a private helper:
```java
private static boolean hasMetaKey(Object params, String key) {
    if (params instanceof Map<?, ?> map && map.get("_meta") instanceof Map<?, ?> meta) {
        return meta.containsKey(key);
    }
    return false;
}
```

Placement notes:
- Apply the gate on the **stateful** dispatch path where `session` is resolved. On the **stateless** path there is no session, so extension methods must return `-32601` (treat `session == null` as not-negotiated).
- The gate runs **before** the read-lock/handler-execution block so rejected calls never touch handler code.
- **Tasks exemption (critical):** the tasks methods (`tasks/get`, `tasks/list`, `tasks/result`, `tasks/delete`) must NOT be gated, or 2025-11-25 clients break. Keep tasks methods registered as **core** handlers (do not register them via `extensionForMethod`). The `TasksExtension` in step 9 only adds the `capabilities.extensions` advertisement — it must NOT put its method names into `extensionMethodOwners`. Verify `server.extensionForMethod("tasks/get") == null`.

### 9. `TasksExtension` — bridge core tasks ↔ draft extension

Read `docs/tasks.md` and `docs/extensions-design.md` → "Tasks as the Reference Extension" first.

Goal: one `TaskRegistry`, advertised two ways. Create `tachyon-mcp-server/src/main/java/me/kpavlov/tachyon/mcp/server/features/tasks/TasksExtension.java`:

```java
public final class TasksExtension implements McpExtension {
    public static final String ID = "io.modelcontextprotocol/tasks"; // confirm vs draft

    @Override public String extensionId() { return ID; }

    // IMPORTANT: return empty so the dispatcher does NOT gate tasks methods.
    // Tasks stay core-compatible; this extension only contributes the advertisement.
    @Override public Set<String> methods() { return Set.of(); }

    @Override public JsonNode serverSettings() { return JsonNodeFactory.instance.objectNode(); }
    // bootstrap is a no-op: TaskRegistry handlers are already registered by McpServer.
}
```

Wiring:
- `McpServer` already owns `TaskRegistry` and registers `tasks/*` handlers via `TaskRegistry.registerHandlers()`. Leave that as the single implementation — do **not** duplicate handlers in the extension.
- Register `TasksExtension` automatically (always-on) so draft clients can negotiate `io.modelcontextprotocol/tasks` while 2025-11-25 clients keep using `capabilities.tasks`. Add it to the default extension list in `McpServer`'s constructor (alongside, not replacing, the core tasks feature).
- The core `capabilities.tasks` advertisement in `InitializeHandler` stays unchanged. When a draft client declares `io.modelcontextprotocol/tasks`, the negotiation logic (step 6) additionally surfaces it under `capabilities.extensions`.
- If the draft defines distinct `_meta` semantics (`modelcontextprotocol.io/task` on augmenting requests — see `docs/tasks.md`), that augmentation handling lives in the tasks dispatch path, not in the extension gate.

This keeps the two front doors (core feature + extension advertisement) sharing exactly one `TaskRegistry` with zero duplicated dispatch logic.

### 10. `ExtensionsStep` builder step (create new file)

Create `tachyon-mcp-server/src/main/java/me/kpavlov/tachyon/mcp/server/builder/ExtensionsStep.java`:
- Holds `BuilderState state`
- `public ExtensionsStep extension(McpExtension extension)` — adds to `state.extensions`, returns `this`
- Transition methods to `SessionStep` and `NetworksStep` with the same pattern as `FeatureStep` (copy `host()`, `port()`, `address()`, `sessionLogRouter()`, `sessionTtl()`, `stateless()`, `endpointPath()`, `readerIdleTimeout()`, `writerIdleTimeout()`)
- Terminal `build()` / `start()` / `startAsync()` methods delegating to `state.build()` / `state.start()` / `state.startAsync()`

In `FeatureStep.java`, add:
```java
public ExtensionsStep extension(McpExtension extension) {
    state.extensions.add(extension);
    return new ExtensionsStep(state);
}
```

### 11. `BuilderState` additions

In `BuilderState.java`:
- Add `List<McpExtension> extensions = new ArrayList<>()`
- Pass `extensions` to `McpServer` constructor in `build()` (the server appends its always-on `TasksExtension` from step 9 — do not duplicate it if the user also added one)

---

## Tests to Write

### Unit tests: `ExtensionNegotiationTest`

In `tachyon-mcp-server/src/test/java/.../server/handlers/ExtensionNegotiationTest.java`:

1. `extensionEnabledWhenBothSidesDeclare` — set up `InitializeHandler` with a test extension, pass matching `ClientCapabilities.extensions`, assert `context.isExtensionEnabled(id)` is true after handle
2. `extensionNotEnabledWhenClientDoesNotDeclare` — client capabilities missing extension, assert not enabled
3. `onConnectionInitCalledForNegotiatedExtension` — verify lifecycle hook called with correct clientSettings
4. `onConnectionInitNotCalledForUnnegotiatedExtension` — verify hook not called when extension absent on client side

### Unit tests: `McpSessionAttributesTest`

In `tachyon-mcp-server/src/test/java/.../server/session/McpSessionAttributesTest.java`:

1. `setAndGetAttribute` — set and retrieve typed attribute
2. `getMissingAttributeReturnsNull`
3. `enableAndCheckExtension`
4. `unenableExtensionReturnsFalse`

### Unit tests: `ExtensionMethodRoutingTest`

In `tachyon-mcp-server/src/test/java/.../server/netty/ExtensionMethodRoutingTest.java` — covers the step 8 dispatcher gate:

1. `rejectsExtensionMethodWhenNotNegotiated` — call an extension-owned method on a session that did not negotiate it → `-32601`
2. `rejectsExtensionMethodMissingMetaEnvelope` — extension negotiated but request lacks `_meta.<extensionId>` → `-32602`
3. `dispatchesExtensionMethodWhenNegotiatedAndMetaPresent` — negotiated + `_meta.<extensionId>` present → handler invoked
4. `extensionMethodAlwaysRejectedInStatelessMode` — stateless server → `-32601`
5. `tasksMethodsAreNotGated` — `tasks/get` dispatches without any extension negotiation or `_meta` (proves the tasks exemption); assert `server.extensionForMethod("tasks/get") == null`

### E2E test: `ExtensionsE2eTest`

In `e2e/src/test/java/.../e2e/ExtensionsE2eTest.java`:

1. `serverAdvertisesExtensionInCapabilities` — initialize, check `$.result.capabilities.extensions` contains extension ID
2. `extensionNotAdvertisedWhenClientDoesNotDeclare` — client sends no extensions, server response omits extension from `capabilities.extensions`
3. `extensionEnabledWhenClientDeclaresIt` — client sends matching extension in capabilities, server enables it (verify via extension's state tracking)
4. `extensionMethodRequiresMetaEnvelope` — negotiate an extension, call its method without `_meta.<id>` → 400/`-32602`; with `_meta.<id>` → success

### E2E test: `TasksBridgeE2eTest`

In `e2e/src/test/java/.../e2e/TasksBridgeE2eTest.java` — proves the core↔extension bridge:

1. `tasksWorkAsCoreFor2025_11_25Client` — client that does NOT declare the tasks extension can still call `tasks/list` (core path), and `capabilities.tasks` is advertised
2. `tasksExtensionAdvertisedWhenNegotiated` — client declares `io.modelcontextprotocol/tasks` → server echoes it under `capabilities.extensions`, and `tasks/list` still works
3. `singleRegistryBacksBothPaths` — a task created via the registry is visible through `tasks/get` regardless of whether the extension was negotiated

---

## Definition of Done

- [ ] `McpExtension` interface compiles
- [ ] `McpSession` has `enabledExtensions` and `attributes`
- [ ] `McpContext` delegates `isExtensionEnabled`, `get/setAttribute` to session
- [ ] `ServerCapabilities` and `ClientCapabilities` have `extensions` field
- [ ] `InitializeHandler` negotiates and enables extensions
- [ ] `JsonRpcDispatcher` gates extension methods on negotiation + `_meta.<extensionId>`
- [ ] Tasks methods are exempt from the gate (`extensionForMethod("tasks/get") == null`)
- [ ] `TasksExtension` registered always-on; single `TaskRegistry` backs core + extension
- [ ] `McpEndpointHandler` stores context in channel attribute, fires `onConnectionClose`
- [ ] `ExtensionsStep` builder step in chain, `FeatureStep.extension()` transitions to it
- [ ] `BuilderState` passes extensions to `McpServer`
- [ ] `McpServer.registerHandler(method, handler)` is public
- [ ] Error messages do not echo raw client input (method names, `_meta` values, settings)
- [ ] All existing tests still pass
- [ ] New unit tests for negotiation, session attributes, and method routing pass
- [ ] E2E tests verify advertisement, `_meta` gating, and the core↔extension tasks bridge
- [ ] `mvn -pl tachyon-mcp-server,e2e test` passes
