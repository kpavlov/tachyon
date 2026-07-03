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
