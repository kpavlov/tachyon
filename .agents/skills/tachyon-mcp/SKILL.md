---
name: tachyon-mcp
description: Build MCP (Model Context Protocol) servers using the [Tachyon MCP](https://github.com/kpavlov/tachyon).
compatibility: Designed for Claude Code on JDK 21+ projects
version: 1.0.0-beta.7
metadata:
    author: Konstantin Pavlov
---
# Tachyon MCP Server Skill️

Make **Java 21+** MCP server. Tachyon lib. Transport = Streamable HTTP (Netty).

## Core

- `TachyonServer.builder()` → `ServerBuilder`. Start here.
- `.start()` (blocking) / `.startAsync()` (non-blocking) → build `Server` + Netty transport → `ServerHandle` (`Closeable`).
- `.build()` → `Server` only, no transport.
- `ServerHandle`: `.server()`, `.port()`.
- `InteractionContext` → session + notifications + server ctx. Every handler gets it (`dev.tachyonmcp.runtime.InteractionContext`).
- ⚡ **Virtual threads**: All handlers (`ToolHandler`, `ResourceHandler`, `PromptHandler`) run on a virtual thread per request. Blocking for I/O is fine — never use `synchronized` (pins carrier thread). Use `ReentrantLock` instead. CPU-bound work → offload to `context.server().executor()`. See `RpcMethodHandler.java` javadoc.

## Quickstart

```java
var handle = TachyonServer.builder()
    .name("my-server")
    .version("1.0")
    .port(8080)
    .start();
// handle.port() → real bound port (matters when port=0)
```

## `ServerBuilder` methods

| Method | What |
|---|---|
| `.info(cfg)` | name, version, description, title, websiteUrl, instructions |
| `.capabilities(cfg)` | tools/resources/prompts/tasks/completions/logging |
| `.session(cfg)` | enabled (off by default = stateless), sessionTtl, SessionLogRouter, SessionStore, SessionIdGenerator |
| `.network(cfg)` | host, port, endpointPath, timeouts, CORS, maxContentLength, ioEngine |
| `.runtime(cfg)` | shutdownGracePeriod |
| `.name(s)` `.port(p)` | shorthands |
| `.tool(handler)` | Sync/Async/ToolHandler |
| `.tool(name, desc, inJson, outJson, fn)` | shorthand — JSON **string** schemas + lambda |
| `.resource(descriptor[, handler])` | static resource (no handler = external URI); handler is Sync `ResourceHandler` or `AsyncResourceHandler` |
| `.prompt(descriptor, handler\|messages)` | `PromptHandler` (simple), `InputRequiredPromptHandler` (sync/MRTR), or `AsyncPromptHandler` |
| `.extension(ext)` | `ServerExtension` plugin |
| `.json(cfg)` | serde + input/output schema validators |
| `.jsonSchemaValidator(v)` | ⚠️ deprecated (removal) → `.json(cfg)` / `.inputSchemaValidator` / `.outputSchemaValidator` |
| `.pipelineCustomizer(c)` | raw Netty pipeline escape hatch |

## Tools 🔧

`SyncToolHandler` (sync, preferred) or `AsyncToolHandler`.

Class — extend `AbstractSyncToolHandler`:

```java
class MyTool extends AbstractSyncToolHandler {
    public MyTool() {
        super(ToolDescriptor.builder()
            .name("my-tool")
            .description("Does something useful")
            .inputSchema(jsonSchema)
            .build());
    }
    @Override
    public ToolResult handle(InteractionContext ctx, ToolArgs args) throws Exception {
        return ToolResult.text("result");
    }
}
```

Lambda — `SyncToolHandler.of(name, description, inputSchema, (ctx, args) -> ...)`:

```java
.tool(SyncToolHandler.of("hello", "Say hello", null,
    (ctx, args) -> ToolResult.text("Hello, world!")))
```

Schema arg can be a `JsonNode` **or** a raw JSON `String` (parsed for you). String overload
takes input + output schemas; there's a matching `.tool(name, desc, inJson, outJson, fn)` shorthand:

```java
.tool("hello", "Say hello",
    """
    {"type":"object","properties":{"name":{"type":"string"}}}
    """, null,                          // inputSchemaJson, outputSchemaJson
    (ctx, args) -> ToolResult.text("Hello, " + args.stringOr("name", "world")))
```

Async — implement `AsyncToolHandler` (or extend `AbstractAsyncToolHandler`), return a
`CompletionStage<ToolResult>`.

`ToolResult` (not generic): `.text(t)` · `.error(msg)` (isError=true) · `.blocks(ContentBlock...)` · `.of(payload)` (structuredContent via Jackson) · `.of(payload, text)` · `.empty()`

Full: `resources/java/ToolHandlerExample.java`

## Resources

Static (fixed URI):

```java
.resource(
    ResourceDescriptor.of("name", "myapp://data/item", "Description", "application/json"),
    (ctx, req) -> TextResourceContents.of(req.uri(), "application/json", jsonData))
```

Template (`{param}`, add after bind):

```java
handle.server().resources()
    .addTemplate(ResourceTemplateEntry.of(
        "template-name", "myapp://data/{id}", "Description", "application/json",
        (ctx, uri, params) -> {
            var id = params.get("id");
            return TextResourceContents.of(uri, "application/json", data);
        }));
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
Default `AUTO` → advertised only when registered. Force with `Mode.ON` / `Mode.OFF`.

| Method | Effect |
|---|---|
| `.tools()` / `.tools(listChanged)` / `.noTools()` | tools |
| `.resources()` / `.resources(subscribe, listChanged)` / `.noResources()` | resources |
| `.prompts()` / `.prompts(listChanged)` / `.noPrompts()` | prompts |
| `.tasks()` / `.tasks(list, cancel, requests)` | tasks |
| `.completions()` | arg autocomplete |
| `.logging()` | logging notifications |

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
The lambda shorthands `SyncToolHandler.of(name, desc, inJson, outJson, fn)` and
`.tool(name, desc, inJson, outJson, fn)` also take String schemas.

## Extensions

```java
public interface ServerExtension extends Extension<MutableInteractionContext> {
    default JsonNode serverSettings() { return JsonNodeFactory.instance.objectNode(); }
    default Set<String> methods() { return Set.of(); }
    default void bootstrap(Server server) {}
    default void onConnectionInit(MutableInteractionContext context, Map<String, JsonNode> clientSettings) {}
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
// buildServer {} → Server without transport
// TachyonServer(port) {} → ServerHandle with Netty transport

val handle = TachyonServer(port = 8080) {
    info {
        name = "demo-server"
        version = "1.0"
        description = "Demo MCP server"
    }
    capabilities {
        tools(true)
        resources(true, true)
        prompts(true)
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
    ToolDescriptor.builder("reverse-echo")
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
- `resources/java/ToolHandlerExample.java` — `ToolDescriptor`, `SyncToolHandler.of()`, `AbstractSyncToolHandler`
- `resources/java/ResourceHandlerExample.java` — `ResourceDescriptor`, `ResourceTemplateEntry`, `ResourceHandler`
- `resources/java/PromptHandlerExample.java` — `PromptDescriptor`, `PromptArgument`, `PromptHandler`
- `resources/java/ConfigReference.java` — `CapabilitiesConfig.Builder`, `NetworkConfig.Builder`, `SessionConfig.Builder`
- `resources/kotlin/ServerBasic.kt` — full server, all features (Kotlin DSL)
- `resources/kotlin/ToolHandlerExample.kt` — suspend handler, `AbstractSyncToolHandler`, `AbstractAsyncToolHandler`, `registerTool`
- `resources/kotlin/ResourceHandlerExample.kt` — static resources, URI templates (Kotlin DSL)
- `resources/kotlin/PromptHandlerExample.kt` — prompt descriptors and handlers (Kotlin DSL)
