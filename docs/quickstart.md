# Quickstart — Tachyon MCP Server

Build and run an MCP server in under 5 minutes.

## Prerequisites

- JDK 21+
- Maven 3.9+

## 1. Add the dependency

```xml
<dependency>
    <groupId>dev.tachyonmcp</groupId>
    <artifactId>tachyon-server</artifactId>
    <version>1.0.0-beta.11</version>
</dependency>
```

For the Kotlin DSL, add `tachyon-server-kotlin` instead (it includes `tachyon-server` transitively).

## 2. Create a server

```java
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;

void main() {
    TachyonServer.builder()
        .name("my-server")
        .version("1.0")
        .tool(ToolHandler.of("greet", "Say hello",
            (ctx, args) -> ToolResult.text("Hello!")))
        .port(8080)
        .start();
}
```

The server binds to `http://127.0.0.1:8080/mcp`.

## 3. Test with curl

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}'
```

## Kotlin

```kotlin
import dev.tachyonmcp.server.TachyonServer
import dev.tachyonmcp.server.features.tools.ToolResult

TachyonServer(port = 8080) {
    info { name = "my-server"; version = "1.0" }
    tool(name = "greet", description = "Say hello") {
        ToolResult.text("Hello!")
    }
}
```

## Next steps

- [Tools](tools.md) — implement tool handlers with input schemas and structured output
- [Resources](resources.md) — expose static and dynamic resources
- [Kotlin DSL](kotlin.md) — full Kotlin DSL reference
- [Extensions](extensions.md) — add protocol extensions
