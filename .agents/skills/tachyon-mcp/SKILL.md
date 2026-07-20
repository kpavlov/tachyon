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
- `TachyonServer`: `.tools()`, `.resources()`, `.prompts()`, `.tasks()` first; then `.port()`, `.close()`, `.config()`.
- Dynamic registration: `.tools().register(handler)`, `.resources().register(...)`, `.prompts().register(...)`.
- Every handler gets `dev.tachyonmcp.runtime.InteractionContext` → session + notifications + server ctx.
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
| `.session(cfg)` | enabled (off by default = stateless), sessionTtl, SessionEventStore, SessionStore, SessionIdGenerator |
| `.network(cfg)` | host, port, endpointPath, timeouts, CORS, maxContentLength, ioEngine |
| `.runtime(cfg)` | shutdownGracePeriod |
| `.monitoring(cfg)` | slow-request diagnostics (off by default) |
| `.name(s)` `.port(p)` | shorthands |
| `.tool(handler)` | Sync/Async/ToolHandler |
| `.tool(configurer, fn)` / `.toolAsync(configurer, fn)` | descriptor-builder conveniences |
| `.tool(name, desc, inJson, outJson, fn)` | shorthand — JSON **string** schemas + lambda |
| `.resource(descriptor/configurer, handler)` | static resource with sync handler |
| `.asyncResource(descriptor/configurer, handler)` | async static resource without casts |
| `.resourceTemplate(...)` / `.asyncResourceTemplate(...)` | sync/async URI template |
| `.prompt(descriptor/configurer, handler\|messages)` | sync prompt |
| `.asyncPrompt(descriptor/configurer, handler)` | async prompt without casts |
| `.extension(ext)` | `ServerExtension` plugin |
| `.json(cfg)` | serde + input/output schema validators |
| ~~`.jsonSchemaValidator(v)`~~ | removed — use `.json(cfg -> cfg.inputSchemaValidator(v).outputSchemaValidator(v))` |
| `.pipelineCustomizer(c)` | raw Netty pipeline escape hatch |

## Tools 🔧

One interface: `ToolHandler`. Use a `ToolHandler.of*` factory or override exactly one `handle`/`handleAsync` × `Args`/`ToolRequest` method. Dispatch calls `handleAsync(ctx, ToolRequest)`; defaults route to your override.

| Need | Factory | Override |
|---|---|---|
| sync, args only | `ToolHandler.of(name, desc, fn)` / `of(configurer, fn)` | `handle(ctx, Args)` |
| sync, full request (progress token) | `ToolHandler.ofRequest(descriptor, fn)` | `handle(ctx, ToolRequest)` |
| async, args only | `ToolHandler.ofAsync(name, fn)` | `handleAsync(ctx, Args)` |
| async, full request | `ToolHandler.ofAsyncRequest(descriptor, fn)` | `handleAsync(ctx, ToolRequest)` |

Sync `handle` runs on a virtual thread; async handlers stay async. Use the request form only for `_meta` (progress token, input responses); `Args` carries neither.

**Prefer `ToolHandler.of…` factories**: one call, no class. `ToolHandler` declares only `descriptor()` and `handleAsync(ctx, ToolRequest)`; sync/args overrides live on `AbstractToolHandler`. Use a class only for instance state or shared setup. Pass its descriptor to `super`:

```java
class MyTool extends AbstractToolHandler {
    MyTool() {
        super(ToolDescriptor.builder().name("my-tool")
            .description("Does something useful").inputSchema(jsonSchema).build());
    }
    @Override
    public ToolResult handle(InteractionContext ctx, Args args) throws Exception {
        return ToolResult.text("result");
    }
}
```

Lambda: `ToolHandler.of(name, description, fn)` or `of(configurer, fn)` for a schema:

```java
.tool(ToolHandler.of("hello", "Say hello",
    (ctx, args) -> ToolResult.text("Hello, world!")))

.tool(ToolHandler.of(b -> b.name("hello").description("Say hello").inputSchema(schema),
    (ctx, args) -> ToolResult.text("Hello, " + args.stringOr("name", "world"))))
```

`inputSchema(...)`/`outputSchema(...)` take `JsonNode` or raw JSON `String`. `.tool(name, desc, inJson, outJson, fn)` builds a sync handler:

```java
.tool("hello", "Say hello",
    """
    {"type":"object","properties":{"name":{"type":"string"}}}
    """, null, // inputSchemaJson, outputSchemaJson
    (ctx, args) -> ToolResult.text("Hello, " + args.stringOr("name", "world")))
```

Async: `ToolHandler.ofAsync(name, (ctx, args) -> CompletionStage<ToolResult>)` or override `handleAsync`.

`ToolResult` (not generic): `.text(t)` · `.error(msg)` (isError=true) · `.blocks(ContentBlock...)` · `.of(payload)` (structuredContent; serialized JSON auto-added as text block) · `.of(payload, text)` · `.raw(json, text)` (pre-serialized JSON) · `.inputRequired(reqs, state)` · `.empty()` · `.withMeta(map)` / `.withMeta(key, value)`

Full: `resources/java/ToolHandlerExample.java`

## Resources

Static fixed URI:

```java
.resource(
    resource -> resource
        .name("name")
        .uri("myapp://data/item")
        .description("Description")
        .mimeType("application/json"),
    ResourceHandler.of((ctx, uri) ->
        TextResourceContents.of(uri, "application/json", jsonData)))
```

Template via builder:

```java
.resourceTemplate(
    template -> template
        .name("template-name")
        .uriTemplate("myapp://data/{id}")
        .description("Description")
        .mimeType("application/json"),
    (ctx, uri, params, uriTemplate) -> {
        var id = ((UriTemplateValue.Scalar) params.get("id")).value();
        return TextResourceContents.of(uri, "application/json", data);
    })
```

Static resources and templates share `ResourceHandler`. Its full parameters are `(context, uri,
params, uriTemplate)`; templates receive immutable parsed values and the original template text,
`UriTemplate` performs matching. Static resources get an empty params map and null template, so
use `ResourceHandler.of((ctx, uri) -> ...)` to drop the two unused parameters.

Use explicit async methods. Sync handlers run blocking-first on virtual threads.

```java
.asyncResource(
    resource -> resource.name("config").uri("myapp://config"),
    ResourceHandler.ofAsync((ctx, uri) -> httpClient.sendAsync(request, BodyHandlers.ofString())
        .thenApply(rsp -> TextResourceContents.of(uri, "application/json", rsp.body()))))
```

Full: `resources/java/ResourceHandlerExample.java`

## Prompts

Sync returns `PromptResult`:

```java
.prompt(
    prompt -> prompt
        .name("rewrite-forecast")
        .description("Rewrites a forecast in a given style"),
    (ctx, request) ->
        PromptResult.messages(List.of(PromptMessage.user("Rewrite this in pirate style."))))
```

Async returns `CompletionStage`:

```java
.asyncPrompt(
    prompt -> prompt.name("rewrite-forecast"),
    (ctx, request) -> service.rewrite(request)
        .thenApply(message -> PromptResult.messages(List.of(PromptMessage.user(message)))))
```

Full: `resources/java/PromptHandlerExample.java`

## Config️

### Capabilities `capabilities(cfg -> ...)`

Configs: `FeatureConfig` (tools/prompts: `mode`, `listChanged`, `pageSize`), `ResourcesConfig` (+ `subscribe`), 
`TasksConfig` (`enabled`, `list`, `cancel`, `requests`, `pageSize`, 
`keepAlive` (default 5 min — retention window for a terminal task's result), 
`pollInterval` (default none — suggested `tasks/get` polling cadence, wire-visible), 
mapping 1:1 to MCP `tasks.list`/`tasks.cancel`/`tasks.requests.tools.call`).

Default `Mode.AUTO` advertises only registered features. Force `Mode.ON`/`Mode.OFF`. **`OFF` also blocks registration**: registry `register` becomes a debug-logged no-op, not merely hidden from `initialize`.

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

Enable logging before publishing structured messages from a handler. `log` accepts every MCP
severity; `info`, `warning`, and `error` are conveniences. The client-selected threshold is applied
per session, with `INFO` used until the client sends `logging/setLevel`.

```java
.capabilities(c -> c.logging())
.tool(
    tool -> tool.name("work").description("Does work"),
    (context, args) -> {
        context.notifications().log(
            LoggingLevel.NOTICE,
            "jobs",
            Map.of("status", "started"));
        return ToolResult.empty();
    })
```

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

Native transports need optional runtime jars (`netty-transport-native-epoll` / `-kqueue` / `-io_uring` with `${os.detected.classifier}`); otherwise `AUTO` falls back to NIO. Explicit unavailable engines throw `UnsupportedOperationException`. See `docs/configuration.md`.

⏳ **Long-tool keep-alive**: `readerIdleTimeout` (60s) closes connections with no **inbound** bytes; waiting clients send none, so >60s tools are reaped mid-compute. Set it to `Duration.ZERO` to disable idle-inbound closing; don't merely raise it. Emit an early server→client message. POST upgrades to SSE; a scheduler sends `:\r\n` every `heartbeatInterval` (15s) for the whole run. Request-level `ToolHandler` triggers (`Args` carries neither):
- `ctx.notifications().progress(token, ...)` — forward the client's `ToolRequest.progressToken()`; **`null` token is silently dropped** (no client opt-in) and sends nothing, so it does not keep the connection alive.
- `ctx.notifications().comment(msg)` — token-free SSE comment (`: msg`); `comment()` = bare `:` heartbeat. Use when no progress token, since a dropped `progress(null, ...)` sends nothing.

**Long task ⇒ emit progress or comment first**. Keep `heartbeatInterval < readerIdleTimeout`; size `readerIdleTimeout` for dead-peer detection, not runtime.

### Session `session(cfg -> ...)`

| Method | Default |
|---|---|
| `.enabled(b)` | false (stateless) |
| `.sessionTtl(d)` | 30s |
| `.janitorInterval(d)` | 5s |
| `.sessionIdGenerator(g)` | `sess_<uuid8>` (derives id from initialize `HttpRequest`) |
| `.sessionEventStore(r)` / `.sessionStore(s)` | null (in-memory) |

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

`ToolDescriptor.builder()` is no-arg only — the `builder(name)` / `builder(name, inJson, outJson)`
overloads have been removed, use `.name(...)` on the builder instead. `.tool(name, desc, inJson, outJson, fn)` also takes String schemas.

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

Register with `.extension(myExtension)`.

## Tests

- Unit: JUnit 6 + AssertJ, `@TempDir`, port `0` = random.
- E2E: `io.modelcontextprotocol.sdk:mcp-core:2.0.0-RC1` client.
- `mvn test` (unit+e2e) · `mvn verify` (+conformance) · `mvn spotless:apply`.

## Kotlin DSL

Kotlin DSL supports **suspend** tool/resource/prompt handlers, `buildServer { }`, and `TachyonServer { }`. Handler lambdas are `suspend` receivers: call suspending APIs directly, without `it`. Prompt lambdas expose `arguments` (renamed from `it` in beta.5).

```kotlin
// buildServer {} → TachyonServer without transport
// TachyonServer(port) {} → TachyonServer with Netty transport

val server = TachyonServer(8080) {
    info {
        name = "demo-server"
        version = "1.0"
    }
    capabilities {
        tools { mode = Mode.ON; listChanged = true }
        resources { mode = Mode.ON; subscribe = true; listChanged = true }
    }
    tool(name = "ping", description = "Simple ping") { ToolResult.text("pong") }
    resource(name = "config", uri = "demo://config", description = "Server configuration") {
        TextResourceContents.of(uri, "application/json", """{"mode":"production"}""")
    }
    prompt(name = "greet", description = "Generates a greeting") {
        listOf(PromptMessage.user("Say hello, ${arguments ?: "friend"}"))
    }
}
```

`tool(inputSchema = ...)` accepts a `JsonNode`, a raw JSON `String`, or a kotlinx `JsonObject`.

### Typed decode/result (Kotlin)

`args.decode<T>()` uses configured serde (kotlinx by default); symmetric `success(value)` returns a typed result:

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
