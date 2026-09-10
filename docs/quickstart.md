---
title: "Quickstart"
weight: 5
sidebar_order: 5
toc: true
description: |-
  Add Tachyon, start an MCP server, and verify it with curl using Java or Kotlin.
---

This guide starts one tool over Streamable HTTP and calls it with `curl`. Use Java or Kotlin; both
examples expose the same `greet` tool at `http://127.0.0.1:8080/mcp`.

## Prerequisites

- JDK 21+
- Maven 3.9+ or Gradle

## 1. Add the dependency

Import the `tachyon-bom` once to pin every Tachyon module to the same version, then add modules
without repeating the version on each one.

Maven:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.tachyonmcp</groupId>
            <artifactId>tachyon-bom</artifactId>
            <version>${tachyon.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>dev.tachyonmcp</groupId>
        <artifactId>tachyon-core</artifactId>
    </dependency>
</dependencies>
```

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation(platform("dev.tachyonmcp:tachyon-bom:$tachyonVersion"))
    implementation("dev.tachyonmcp:tachyon-core")
}
```

Set `tachyon.version` or `tachyonVersion` to the latest release listed on
[Maven Central](https://central.sonatype.com/artifact/dev.tachyonmcp/tachyon-bom). For the Kotlin
DSL, add `tachyon-kotlin` instead; it includes `tachyon-core` transitively.

## 2. Create a server

```java
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.TachyonServer;

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

Run `MyMcpServer.main()`. The process listens at `http://127.0.0.1:8080/mcp` until you stop it.

## 3. Test with curl

```bash
curl --include --request POST http://127.0.0.1:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}'
```

The response contains the negotiated protocol version and server capabilities. This server is
stateless by default, so it doesn't return an `Mcp-Session-Id` header.

List and call the tool:

```bash
curl --request POST http://127.0.0.1:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-11-25" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

curl --request POST http://127.0.0.1:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-11-25" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"greet","arguments":{}}}'
```

## Kotlin

```kotlin
import dev.tachyonmcp.api.server.features.tools.ToolResult
import dev.tachyonmcp.kotlin.server.TachyonServer

TachyonServer(port = 8080) {
    info { name = "my-server"; version = "1.0" }
    tool(name = "greet", description = "Say hello") {
        ToolResult.text("Hello!")
    }
}
```

## Next steps

- [Tools](features/tools.md) — implement tool handlers with input schemas and structured output
- [Resources](features/resources.md) — expose static and dynamic resources
- [Kotlin DSL](kotlin/) — full Kotlin DSL reference
- [Extensions](extensions/) — add protocol extensions
