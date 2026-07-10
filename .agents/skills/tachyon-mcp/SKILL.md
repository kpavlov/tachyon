---
name: tachyon-mcp
description: Build MCP (Model Context Protocol) servers using the [Tachyon MCP](https://github.com/kpavlov/tachyon).
compatibility: Designed for Claude Code on JDK 21+ projects
version: 1.0.0-SNAPSHOT
metadata:
    author: Konstantin Pavlov
---
# Tachyon MCP Server Skill️

Make **Java 21+** MCP server. Tachyon lib. Transport = Streamable HTTP (Netty).

## Core

- `TachyonServer.builder()` → `ServerBuilder`. Start here.
- `.start()` (blocking) → builds `TachyonServer` (`AutoCloseable`) with Netty transport bound.
- `.build()` → `TachyonServer` only, no transport.
- `TachyonServer`: `.port()`, `.close()`, `.config()`, `.getTool(name)`, `.registerTool(handler)` (the handler carries its own descriptor).
- `InteractionContext` → session + notifications + server ctx. Every handler gets it (`dev.tachyonmcp.runtime.InteractionContext`).
- ⚡ **Virtual threads**: All handlers (`ToolHandler`, `ResourceHandler`, `PromptHandler`) run on a virtual thread per request. Blocking for I/O is fine — never use `synchronized` (pins carrier thread). Use `ReentrantLock` instead. CPU-bound work → offload to `context.server().executor()`. See `RpcMethodHandler.java` javadoc.

## Quickstart

```java
var server = TachyonServer.builder()
    .info(b -> b.name("my-server").version("1.0"))
    .port(8080)
    .start();
// server.port() → real bound port (matters when port=0)
```

## `ServerBuilder` methods

| Method | What |
|---|---|
| `.info(cfg)` | name, version, description, title, websiteUrl, instructions |
| `.capabilities(cfg)` | tools/resources/prompts/tasks/completions/logging |
| `.session(cfg)` | enabled (off by default = stateless), sessionTtl, SessionLogRouter, SessionStore, SessionIdGenerator |
| `.network(cfg)` | host, port, endpointPath, timeouts, CORS, maxContentLength, ioEngine |
| `.runtime(cfg)` | shutdownGracePeriod |
| `.monitoring(cfg)` | slow-request diagnostics (off by default) |
| `.name(s)` `.port(p)` | shorthands |
| `.tool(handler)` | Sync/Async/ToolHandler |
| `.tool(name, desc, inJson, outJson, fn)` | shorthand — JSON **string** schemas + lambda |
| `.resource(descriptor[, handler])` | static resource (no handler = external URI); handler is Sync `ResourceHandler` or `AsyncResourceHandler` |
| `.resourceTemplate(entry)` | URI template (use in builder chain instead of post-start `addTemplate`) |
| `.prompt(descriptor, handler\|messages)` | `PromptHandler` (simple), `InputRequiredPromptHandler` (sync/MRTR), or `AsyncPromptHandler` |
| `.extension(ext)` | `ServerExtension` plugin |
| `.json(cfg)` | serde + input/output schema validators |
| `.jsonSchemaValidator(v)` | ⚠️ deprecated (removal) → `.json(cfg)` / `.inputSchemaValidator` / `.outputSchemaValidator` |
| `.pipelineCustomizer(c)` | raw Netty pipeline escape hatch |

## Tools 🔧

One interface: `ToolHandler`. Override exactly one method — `handle`/`handleAsync` × `ToolArgs`/`ToolRequest` — or use a `ToolHandler.of*` factory. Dispatch calls `handleAsync(ctx, ToolRequest)`; the defaults route to whichever you provide.

| Need | Factory | Override |
|---|---|---|
| sync, args only | `ToolHandler.of(name, desc, fn)` / `of(configurer, fn)` | `handle(ctx, ToolArgs)` |
| sync, full request (progress token) | `ToolHandler.ofRequest(descriptor, fn)` | `handle(ctx, ToolRequest)` |
| async, args only | `ToolHandler.ofAsync(name, fn)` | `handleAsync(ctx, ToolArgs)` |
| async, full request | `ToolHandler.ofAsyncRequest(descriptor, fn)` | `handleAsync(ctx, ToolRequest)` |

Blocking is fine — sync `handle` runs on a virtual thread. Async handlers stay async (no blocking detour). Only override the request form when you need `_meta` (progress token, input responses); `ToolArgs` carries neither.

**Prefer the `ToolHandler.of…` factories** — one call, no class. The `ToolHandler` interface declares only `descriptor()` and `handleAsync(ctx, ToolRequest)`; the sync/args override points live on `AbstractToolHandler`. Reach for a class only when the handler needs instance state or shared setup.

Class (when a factory won't do) — extend `AbstractToolHandler` (pass the descriptor to `super`):

```java
class MyTool extends AbstractToolHandler {
    MyTool() {
        super(ToolDescriptor.builder()
            .name("my-tool").description("Does something useful").inputSchema(jsonSchema).build());
    }
    @Override
    public ToolResult handle(InteractionContext ctx, ToolArgs args) throws Exception {
        return ToolResult.text("result");
    }
}
```

Lambda — `ToolHandler.of(name, description, (ctx, args) -> ...)`, or `of(configurer, fn)` for a schema:

```java
.tool(ToolHandler.of("hello", "Say hello",
    (ctx, args) -> ToolResult.text("Hello, world!")))

.tool(ToolHandler.of(
    b -> b.name("hello").description("Say hello").inputSchema(schema),
    (ctx, args) -> ToolResult.text("Hello, " + args.stringOr("name", "world"))))
```

`inputSchema(...)`/`outputSchema(...)` take a `JsonNode` **or** a raw JSON `String`. Shorthand
`.tool(name, desc, inJson, outJson, fn)` builds the sync handler for you:

```java
.tool("hello", "Say hello",
    """
    {"type":"object","properties":{"name":{"type":"string"}}}
    """, null,                          // inputSchemaJson, outputSchemaJson
    (ctx, args) -> ToolResult.text("Hello, " + args.stringOr("name", "world")))
```

Async — `ToolHandler.ofAsync(name, (ctx, args) -> CompletionStage<ToolResult>)` (or override `handleAsync`).

`ToolResult` (not generic): `.text(t)` · `.error(msg)` (isError=true) · `.blocks(ContentBlock...)` · `.of(payload)` (structuredContent; serialized JSON auto-added as text block) · `.of(payload, text)` · `.raw(json, text)` (pre-serialized JSON) · `.inputRequired(reqs, state)` · `.empty()` · `.withMeta(map)` / `.withMeta(key, value)`

Full: `resources/java/ToolHandlerExample.java`

## Resources

Static (fixed URI):

```java
.resource(
    ResourceDescriptor.of("name", "myapp://data/item", "Description", "application/json"),
    (ctx, req) -> TextResourceContents.of(req.uri(), "application/json", jsonData))
```

Template — add via builder:

```java
.resourceTemplate(ResourceTemplateEntry.of(
    "template-name", "myapp://data/{id}", "Description", "application/json",
    (ctx, uri, params) -> {
        var id = params.get("id");
        return TextResourceContents.of(uri, "application/json", data);
    }))
```

Async — return a `CompletionStage`: implement `AsyncResourceHandler` (static resource) or
`AsyncResourceTemplateHandler` (template). Sync `ResourceHandler`/`ResourceTemplateHandler`
run blocking-first on virtual threads.

```java
AsyncResourceHandler h = (ctx, req) ->
    httpClient.sendAsync(request, BodyHandlers.ofString())
        .thenApply(rsp -> TextResourceContents.of(req.uri(), "application/json", rsp.body()));
.resource(descriptor, h)
```

Full: `resources/java/ResourceHandlerExample.java`

## Prompts

Simple — `PromptHandler.getMessages(arguments)` returns messages:

```java
.prompt(
    PromptDescriptor.of("rewrite-forecast", "Rewrites a forecast in a given style"),
    args -> List.of(PromptMessage.user("Rewrite this in pirate style.")))
```

Sync/async/MRTR — `InputRequiredPromptHandler.handle(ctx, PromptRequest)` returns a
`PromptHandlerResult` (`.messages(list)` or `.inputRequired(reqs, state)`); `AsyncPromptHandler`
returns a `CompletionStage`.

```java
.prompt(descriptor, (ctx, request) ->
    PromptHandlerResult.messages(List.of(PromptMessage.user("Rewrite: " + request.arguments()))))
```

Full: `resources/java/PromptHandlerExample.java`

## Config️

### Capabilities `capabilities(cfg -> ...)`
Each feature has its own config: `FeatureConfig` (tools/prompts — `mode`, `listChanged`, `pageSize`), `ResourcesConfig` (adds `subscribe`), `TasksConfig` (`enabled`, `list`, `cancel`, `requests`, `pageSize` — maps 1:1 to MCP `tasks.list`/`tasks.cancel`/`tasks.requests.tools.call`).
Default `Mode.AUTO` → advertised only when registered. Force with `Mode.ON` / `Mode.OFF`. **`OFF` also blocks registration** — `register`/`add` on that registry becomes a no-op (logged at debug), not just hidden from `initialize`.

| Method | Effect |
|---|---|
| `.tools(FeatureConfig)` / `.resources(ResourcesConfig)` / `.prompts(FeatureConfig)` / `.tasks(TasksConfig)` | set the full nested config |
| `.tools()` / `.tools(listChanged)` / `.noTools()` | shortcut: tools |
| `.resources()` / `.resources(subscribe, listChanged)` / `.noResources()` | shortcut: resources |
| `.prompts()` / `.prompts(listChanged)` / `.noPrompts()` | shortcut: prompts |
| `.tasks()` / `.tasks(list, cancel, requests)` | shortcut: tasks (`enabled=true`) |
| `.toolsMode(m)` / `.toolsListChanged(b)` / `.toolsPageSize(n)` (+ `resources*`/`prompts*`/`tasks*` siblings) | flat per-field setters; chain onto the shortcuts above, e.g. `c.tools().toolsPageSize(20)` |
| `.completions()` | arg autocomplete |
| `.logging()` | logging notifications |

Kotlin DSL nests instead: `capabilities { tools { mode = Mode.ON; pageSize = 20 }; tasks { enabled = true; list = true } }`.

### Network `network(cfg -> ...)`
| Method | Default |
|---|---|
| `.host(s)` | `127.0.0.1` |
| `.port(p)` | **required** before `start()` |
| `.endpointPath(p)` | `/mcp` |
| `.readerIdleTimeout(d)` / `.writerIdleTimeout(d)` | 60s / 5min |
| `.heartbeatInterval(d)` | 15s (`<= 0` disables) |
| `.maxContentLength(b)` | 1MB |
| `.allowedOrigins(...)` | none (all denied) |
| `.allowNullOrigin(b)` / `.allowPrivateNetworks(b)` | false |
| `.allowedHeaders(...)` | none |
| `.ioEngine(e)` | `NettyIoEngine.AUTO` (io_uring → epoll → kqueue → NIO) |

Native transports need optional runtime jars (`netty-transport-native-epoll` / `-kqueue` / `-io_uring` with `${os.detected.classifier}`); without them `AUTO` falls back to NIO. Explicit unavailable engine throws `UnsupportedOperationException`. See `docs/configuration.md`.

⏳ **Keep-alive for long tools** — `readerIdleTimeout` (60s) closes any connection with no **inbound** bytes for that long, and a client waiting for a reply sends none — so a tool slower than 60s gets reaped mid-compute. Set `readerIdleTimeout` to `Duration.ZERO` to disable closing idle inbound stream. Don't just raise the timeout. Emit an early server→client message: the POST upgrades to SSE and a scheduler sends `:\r\n` heartbeats every `heartbeatInterval` (15s), keeping the stream alive for the whole run. Two triggers (request-level `ToolHandler` only — `ToolArgs` carries neither):
- `ctx.notifications().progress(token, ...)` — forward the client's `ToolRequest.progressToken()`; **null token throws**.
- `ctx.notifications().comment(msg)` — token-free SSE comment (`: msg`); `comment()` = bare `:` heartbeat. Use when no progress token.

Rule: **long task ⇒ emit progress or comment first**; keep `heartbeatInterval < readerIdleTimeout`; size `readerIdleTimeout` for dead-peer detection, not tool runtime.

### Session `session(cfg -> ...)`

| Method | Default |
|---|---|
| `.enabled(b)` | false (stateless) |
| `.sessionTtl(d)` | 30s |
| `.janitorInterval(d)` | 5s |
| `.sessionIdGenerator(g)` | `sess_<uuid8>` (derives id from initialize `HttpRequest`) |
| `.sessionLogRouter(r)` / `.sessionStore(s)` | null (in-memory) |

### Runtime `runtime(cfg -> ...)`

| Method | Default |
|---|---|
| `.shutdownGracePeriod(d)` | 5s (drain in-flight handlers on close; `ZERO` = interrupt now) |
| `.requestTimeout(d)` | 60s (timeout for pending requests sent to client) |

### Monitoring `monitoring(cfg -> ...)`

| Method | Default |
|---|---|
| `.slowRequestLogging()` / `.slowRequestLogging(b)` | `false` (gate all slow-request diagnostics) |
| `.slowRequestThreshold(d)` | `10s` (slow-request threshold) |

## JSON Schema

> ⚠️ **Jackson 3** — `tools.jackson:jackson-databind:3.x`, NOT Jackson 2. Import `tools.jackson.databind.{ObjectMapper,JsonNode}` (**not** `com.fasterxml.jackson.*`). Use `JsonNode.asString()` (**not** `asText()`).

`ToolDescriptor.builder().inputSchema(...)` / `.outputSchema(...)` accept a raw JSON `String`
**or** a Jackson `JsonNode` (same for `PromptDescriptor.builder().inputSchema(...)`):

```java
ToolDescriptor.builder()
    .name("get_weather")
    .inputSchema("""
        { "type": "object",
          "properties": { "city": { "type": "string", "description": "City name" } },
          "required": ["city"] }
        """)
    .build();
```

(`builder(name)` and `builder(name, inJson, outJson)` still exist but are deprecated.)
The `.tool(name, desc, inJson, outJson, fn)` shorthand also takes String schemas.

## Extensions

```java
public interface ServerExtension extends Extension<ChannelContext> {
    default JsonNode serverSettings() { return JsonNodeFactory.instance.objectNode(); }
    default Set<String> methods() { return Set.of(); }
    default boolean requiresMetaEnvelope() { return true; }
    default void bootstrap(ServerEngine server) {}
    default void onConnectionInit(ChannelContext context, Map<String, JsonNode> clientSettings) {}
}
```

Register: `.extension(myExtension)`

## Tests

- Unit: JUnit 6 + AssertJ, `@TempDir`, port `0` = random.
- E2E: `io.modelcontextprotocol.sdk:mcp-core:2.0.0-RC1` client.
- `mvn test` (unit+e2e) · `mvn verify` (+conformance) · `mvn spotless:apply`.

## Kotlin DSL

Also available — Kotlin DSL with **suspend** tool/resource/prompt handlers, `buildServer { }`, and `TachyonServer { }`.
Handler lambdas are `suspend` receivers — call suspending APIs directly, no `it` parameter.
The prompt lambda exposes `arguments` (renamed from `it` in beta.5).

```kotlin
// buildServer {} → TachyonServer without transport
// TachyonServer(port) {} → TachyonServer with Netty transport

val server = TachyonServer(port = 8080) {
    info {
        name = "demo-server"
        version = "1.0"
        description = "Demo MCP server"
    }
    capabilities {
        tools { mode = Mode.ON; listChanged = true }
        resources { mode = Mode.ON; subscribe = true; listChanged = true }
        prompts { mode = Mode.ON; listChanged = true }
    }
    tool(name = "ping", description = "Simple ping") {
        ToolResult.text("pong")
    }
    resource(
        name = "config",
        uri = "demo://config",
        description = "Server configuration",
    ) {
        TextResourceContents.of(uri, "application/json", """{"mode":"production"}""")
    }
    prompt(name = "greet", description = "Generates a greeting") {
        listOf(PromptMessage.user("Say hello, ${arguments ?: "friend"}"))
    }
}
```

`tool(inputSchema = ...)` accepts a `JsonNode`, a raw JSON `String`, or a kotlinx `JsonObject`.

### Typed decode/result (Kotlin)

`args.decode<T>()` decodes tool arguments through the configured serde (kotlinx by default). Symmetric `success(value)` returns a typed result:

```kotlin
@Serializable
data class GreetArgs(val name: String, val greeting: String = "Hello")

@Serializable
data class GreetReply(val message: String)

tool(name = "greet", description = "Typed greet", inputSchema = ..., outputSchema = ...) {
    val input = args.decode<GreetArgs>()       // uses configured serde
    success(GreetReply("${input.greeting}, ${input.name}!"), "custom text")
}
```

- `args.decode<T>()` — decodes through the configured serde (kotlinx by default), honors custom `Json` config
- `scope.success(value)` — mirrors `decode`, defers serialization to the configured serializer
- `scope.success(value, text)` — structured + human-readable text fallback

Post-build registration with `registerTool`:

```kotlin
server.registerTool(
    ToolDescriptor.builder()
        .name("reverse-echo")
        .description("Echo reversed message")
        .inputSchema(schema)
        .build(),
) {
    ToolResult.text(args.string("message").reversed())
}
```

## Resource files

Load on demand (next to this skill):

- [resources/java/ServerBasic.java](resources/java/ServerBasic.java) — full server, all features
- [resources/java/ToolHandlerExample.java](resources/java/ToolHandlerExample.java) — `ToolDescriptor`, `ToolHandler.of()`, `extends AbstractToolHandler`, long-running keep-alive (`handle(ctx, ToolRequest)` + `progress()`)
- [resources/java/ResourceHandlerExample.java](resources/java/ResourceHandlerExample.java) — `ResourceDescriptor`, `ResourceTemplateEntry`, `ResourceHandler`
- [resources/java/PromptHandlerExample.java](resources/java/PromptHandlerExample.java) — `PromptDescriptor`, `PromptArgument`, `PromptHandler`
- [resources/java/ConfigReference.java](resources/java/ConfigReference.java) — `CapabilitiesConfig.Builder`, `NetworkConfig.Builder`, `SessionConfig.Builder`
- [resources/kotlin/ServerBasic.kt](resources/kotlin/ServerBasic.kt) — full server, all features (Kotlin DSL)
- [resources/kotlin/ToolHandlerExample.kt](resources/kotlin/ToolHandlerExample.kt) — suspend handler, `extends AbstractToolHandler` (`handle`/`handleAsync`), `registerTool`
- [resources/kotlin/ResourceHandlerExample.kt](resources/kotlin/ResourceHandlerExample.kt) — static resources, URI templates (Kotlin DSL)
- [resources/kotlin/PromptHandlerExample.kt](resources/kotlin/PromptHandlerExample.kt) — prompt descriptors and handlers (Kotlin DSL)
