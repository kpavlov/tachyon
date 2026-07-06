---
name: tachyon-mcp
description: Build MCP (Model Context Protocol) servers using the [Tachyon MCP](https://github.com/kpavlov/tachyon).
compatibility: Designed for Claude Code on JDK 21+ projects
version: 1.0.0-beta.4
metadata:
    author: Konstantin Pavlov
---
# Tachyon MCP Server Skill️

Make **Java 21+** MCP server. Tachyon lib. Transport = Streamable HTTP (Netty).

## Core

- `TachyonServer.builder()` → `ServerBuilder`. Start here.
- `.start()` (blocking) / `.startAsync()` (non-blocking) → build `McpServer` + Netty transport → `McpServerHandle` (`Closeable`).
- `.build()` → `McpServer` only, no transport.
- `McpServerHandle`: `.server()`, `.port()`.
- `McpContext` → session + notifications + server ctx. Every handler gets it.

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
| `.resource(descriptor[, handler])` | static resource (no handler = external URI) |
| `.prompt(descriptor, handler\|messages)` | prompt |
| `.extension(ext)` | McpExtension plugin |
| `.jsonSchemaValidator(v)` | custom validator (default Networknt) |
| `.pipelineCustomizer(c)` | raw Netty pipeline escape hatch |

## Tools 🔧

`SyncToolHandler` (sync, preferred) or `AsyncToolHandler`.

Class — extend `AbstractSyncToolHandler`:

```java
class MyTool extends AbstractSyncToolHandler {
    public MyTool() {
        super(ToolDescriptor.builder("my-tool")
            .description("Does something useful")
            .inputSchema(jsonSchema)
            .build());
    }
    @Override
    public ToolResult handle(McpContext ctx, ToolArgs args) throws Exception {
        return ToolResult.text("result");
    }
}
```

Lambda — `SyncToolHandler.of(name, description, inputSchema, (ctx, args) -> ...)`:

```java
.tool(SyncToolHandler.of("hello", "Say hello", null,
    (ctx, args) -> ToolResult.text("Hello, world!")))
```

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

Full: `resources/java/ResourceHandlerExample.java`

## Prompts

```java
.prompt(
    PromptDescriptor.of("rewrite-forecast", "Rewrites a forecast in a given style"),
    args -> List.of(PromptMessage.user("Rewrite this in pirate style.")))
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
| `.sessionIdGenerator(g)` | `sess_<uuid8>` (derives id from initialize `HttpRequest`) |
| `.sessionLogRouter(r)` / `.sessionStore(s)` | null (in-memory) |

### Runtime `runtime(cfg -> ...)`

| Method | Default |
|---|---|
| `.shutdownGracePeriod(d)` | 5s (drain in-flight handlers on close; `ZERO` = interrupt now) |

## JSON Schema

> ⚠️ **Jackson 3** — `tools.jackson:jackson-databind:3.x`, NOT Jackson 2. Import `tools.jackson.databind.{ObjectMapper,JsonNode}` (**not** `com.fasterxml.jackson.*`). Use `JsonNode.asString()` (**not** `asText()`).

```java
private static final JsonNode INPUT_SCHEMA = MAPPER.readTree("""
    { "type": "object",
      "properties": { "city": { "type": "string", "description": "City name" } },
      "required": ["city"] }
    """);
```

→ `ToolDescriptor.builder("name").inputSchema(INPUT_SCHEMA).build()`

## Extensions

```java
public interface McpExtension extends Extension<McpContext> {
    default JsonNode serverSettings() { return JsonNodeFactory.instance.objectNode(); }
    default Set<String> methods() { return Set.of(); }
    default void bootstrap(McpServer server) {}
    default void onConnectionInit(McpContext context, Map<String, JsonNode> clientSettings) {}
}
```

Register: `.extension(myExtension)`

## Tests

- Unit: JUnit 6 + AssertJ, `@TempDir`, port `0` = random.
- E2E: `io.modelcontextprotocol.sdk:mcp-core:2.0.0-RC1` client.
- `mvn test` (unit+e2e) · `mvn verify` (+conformance) · `mvn spotless:apply`.

## Kotlin DSL

Also available — Kotlin DSL with suspend tool handlers, `buildServer { }`, and `TachyonServer { }`.

```kotlin
// buildServer {} → McpServer without transport
// TachyonServer(port) {} → McpServerHandle with Netty transport

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
    prompt(name = "greet", description = "Generates a greeting") { _ ->
        listOf(PromptMessage.user("Say hello"))
    }
}
```

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
