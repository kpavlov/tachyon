# Quickstart — Tachyon MCP Server

Build and run an MCP server in under 5 minutes.

## Prerequisites

- JDK 21+
- Maven 3.9+ or Gradle

## 1. Add the dependency

Maven:

```xml
<dependency>
    <groupId>dev.tachyonmcp</groupId>
    <artifactId>tachyon-core</artifactId>
    <version>1.0.0-beta.22</version> <!-- get latest version from Maven Central -->
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("dev.tachyonmcp:tachyon-core:1.0.0-beta.22")
}
```

For the Kotlin DSL, add `tachyon-kotlin` instead (it includes `tachyon-core` transitively).

## 2. Create a server

```java
import dev.tachyonmcp.core.server.TachyonServer;
import dev.tachyonmcp.api.server.features.tools.ToolResult;

public final class MyMcpServer {
    public static void main(String[] args) {
        var server = TachyonServer.builder()
                .name("my-server")
                .version("1.0")
                .withTools(tools -> tools.register(
                        tool -> tool.name("greet").description("Say hello"),
                        (ctx, request) -> ToolResult.text("Hello!")))
                .port(8080)
                .build();
        server.start();
    }
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
import dev.tachyonmcp.kotlin.server.TachyonServer
import dev.tachyonmcp.api.server.features.tools.ToolResult

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
