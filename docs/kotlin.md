# Kotlin DSL — Tachyon MCP Server

The `tachyon-server-kotlin` module wraps `ServerBuilder` with a coroutine-first DSL, suspend tool handlers, and type-safe scope classes.

## Dependency

```xml
<dependency>
    <groupId>dev.tachyonmcp</groupId>
    <artifactId>tachyon-server-kotlin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Entry points

```kotlin
// Start Netty transport — returns McpServerHandle
val handle = TachyonServer(port = 8080) { /* configure */ }

// Lowercase alias — identical behaviour
val handle = tachyonServer(port = 8080) { /* configure */ }

// Server logic only, no transport — for testing
val server: McpServer = buildServer { /* configure */ }
```

## Full example

```kotlin
import dev.tachyonmcp.server.TachyonServer
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.domain.TextResourceContents
import dev.tachyonmcp.server.features.tools.ToolResult

val handle = TachyonServer(port = 8080) {
    info {
        name = "demo-server"
        version = "1.0"
        description = "Demo MCP server"
    }
    capabilities {
        tools(listChanged = true)
        resources(subscribe = true, listChanged = true)
        prompts(listChanged = true)
    }
    session {
        enabled = true
        sessionTtl = 5.minutes
        sessionIdGenerator = { "sess_" + Uuid.random().toHexString() }
    }
    tool(name = "ping", description = "Ping the server") {
        ToolResult.text("pong")
    }
    runtime {
        shutdownGracePeriod = 5.seconds
    }
    resource(
        name = "config",
        uri = "demo://config",
        description = "Server configuration",
        mimeType = "application/json",
    ) {
        TextResourceContents.of(uri, "application/json", """{"env":"prod"}""")
    }
    prompt(name = "greet", description = "Greeting prompt") { _ ->
        listOf(PromptMessage.user("Say hello"))
    }
}
```

## Tool handlers

Tool lambdas are `suspend` functions with access to `ToolScope`:

```kotlin
tool(name = "reverse", description = "Reverse a string") {
    // this: ToolScope
    // ctx: McpContext
    // args: ToolArgs
    val msg = args.string("message")
    ToolResult.text(msg.reversed())
}
```

For class-based handlers, extend `AbstractSyncToolHandler` or `AbstractAsyncToolHandler` from `tachyon-server` — they work unchanged from Kotlin.

## Tool schemas

`inputSchema` / `outputSchema` accept three shapes on every registration overload
(`tool(...)` in the DSL, `Server.registerTool(...)` post-build, `toolDescriptor { }`):

```kotlin
// Jackson JsonNode
tool("a", inputSchema = jacksonNode) { /* ... */ }

// Raw JSON string — parsed by Tachyon
tool(
    "b",
    inputSchema = """{"type":"object","properties":{"msg":{"type":"string"}}}""",
    outputSchema = """{"type":"object","properties":{"echo":{"type":"string"}}}""",
) { /* ... */ }

// kotlinx.serialization JsonObject — requires kotlinx-serialization-json (optional)
tool("c", inputSchema = buildJsonObject { put("type", "object") }) { /* ... */ }
```

Schema roots are validated at registration time: a schema whose root does not declare
`"type": "object"` fails fast with `IllegalArgumentException` instead of surfacing later
in the MCP client. Tool descriptions longer than 2048 characters log a warning —
clients may truncate them.

## kotlinx.serialization integration

`kotlinx-serialization-json` is an **optional** dependency of `tachyon-server-kotlin`.
Add it to use `JsonObject` schemas, `args.decode<T>()`, and `structured(value)` / `success(value)`:

```xml
<dependency>
    <groupId>org.jetbrains.kotlinx</groupId>
    <artifactId>kotlinx-serialization-json</artifactId>
</dependency>
```

```kotlin
@Serializable data class EchoArgs(val message: String, val loud: Boolean = false)

@Serializable data class EchoReply(val echo: String)

tool(
    "echo",
    inputSchema = """{"type":"object","properties":{"message":{"type":"string"}}}""",
    outputSchema = """{"type":"object","properties":{"echo":{"type":"string"}}}""",
) {
    val input = args.decode<EchoArgs>() // typed decode via configured serde
    structured(EchoReply(input.message)) // structuredContent + JSON text fallback
}
```

The default kotlinx serde ignores unknown keys; configure a strict `Json` via
`json { serde = KxSerializationSerde(Json { ignoreUnknownKeys = false }) }`.
`structured(value)` requires the value to encode to a JSON object and pairs with the
declared `outputSchema`.

### Typed decode/result via configured serde

Kotlin extensions bridge the gap between the existing Java typed API and the
configured serde in the Kotlin DSL:

| Method | Routes through | Behaviour |
|---|---|---|
| `args.decode<T>()` | server-configured `PayloadDeserializer` | Honors custom `Json` config |
| `scope.success(value)` | server-configured `PayloadSerializer` | Deferred serialization at encode time |
| `scope.success(value, text)` | server-configured `PayloadSerializer` | Structured + human-readable text |

`decode<T>` uses `T::class.java → ToolArgs.decode(Class<T>)`, which routes
through the deserializer set in `json { serde = ... }`.

```kotlin
@Serializable data class GreetArgs(val name: String, val greeting: String = "Hello")
@Serializable data class GreetReply(val message: String)

tool(name = "greet", inputSchema = ..., outputSchema = ...) {
    val input = args.decode<GreetArgs>()          // honors configured serde
    success(GreetReply("${input.greeting}, ${input.name}!"), "greeting response")  // symmetric typed result
}
```

## ToolArgs accessors

| Call | Behaviour |
|---|---|
| `args.string("k")` / `intValue` / `boolValue` / `doubleValue` | Required — throws when missing |
| `args.stringOrNull("k")` / `intOrNull` / `booleanOrNull` / `doubleOrNull` | Returns `null` when missing |
| `args.stringOr("k", "d")` / `int("k", 0)` / `boolean("k", true)` / `double("k", 0.0)` | Falls back to default |
| `args.decode<T>()` | typed decode via configured serde (default kotlinx ignores unknown keys) |

## Scope reference

| Scope | Builder method | Properties |
|---|---|---|
| `ServerInfoScope` | `info { }` | `name`, `version`, `description`, `title`, `instructions` |
| `CapabilitiesScope` | `capabilities { }` | `tools()`, `resources()`, `prompts()`, `tasks()`, `logging()`, `completions()` |
| `NetworkScope` | `network { }` | `host`, `port`, `endpointPath`, `allowedOrigins`, `maxContentLength` |
| `SessionScope` | `session { }` | `enabled`, `sessionTtl`, `sessionIdGenerator` |
| `RuntimeScope` | `runtime { }` | `shutdownGracePeriod` |
| `ToolScope` | tool lambda | `ctx`, `args` |
| `ResourceScope` | resource lambda | `ctx`, `request`, `uri` |

## Post-build registration

Add tools after the server starts — useful for dynamic registration:

```kotlin
val server = buildServer { /* base config */ }
server.registerTool(
    ToolDescriptor.builder("echo").description("Echo a message").build(),
) {
    ToolResult.text(args.string("msg"))
}
```

## Netty pipeline customization

```kotlin
TachyonServer(port = 8080) {
    pipelineCustomizer {
        addLast("metrics", MetricsHandler())
    }
}
```

## ToolResult factories

| Call | Behaviour |
|---|---|
| `ToolResult.text(t)` | Text content block |
| `ToolResult.error(msg)` | `isError = true` |
| `ToolResult.blocks(vararg b)` | Multiple content blocks |
| `ToolResult.of(payload)` | POJO → `structuredContent` via Jackson |
| `ToolResult.of(payload, text)` | Structured + human-readable text |
| `ToolResult.empty()` | No content |

`structuredContent` requires a JSON object shape. Tachyon rejects primitives and arrays at runtime.

With kotlinx-serialization on the classpath, prefer `structured(value)` inside a tool lambda —
it encodes any `@Serializable` value and emits both `structuredContent` and the JSON text fallback.

## Testing

Use `buildServer { }` with `port = 0` for zero-setup E2E tests:

```kotlin
val server = buildServer { tool("ping") { ToolResult.text("pong") } }
val transport = NettyServerTransport.start(server, 0)
// transport.port() → ephemeral port
```

Run Kotlin tests only:

```bash
mvn test -am -f tachyon-server-kotlin/pom.xml
```

---

**See also:** [Tools](tools.md) · [Resources](resources.md) · [Tasks](tasks.md) · [Extensions](extensions.md) · [Quickstart](quickstart.md)
