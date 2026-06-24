

[![Maven Central](https://img.shields.io/maven-central/v/dev.tachyonmcp/tachyon-server)](https://repo1.maven.org/maven2/dev/tachyonmcp/tachyon-server/)
[![Build](https://github.com/kpavlov/tachyon/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/kpavlov/tachyon/actions/workflows/build.yml)
[![Conformance 46 of 46](https://img.shields.io/badge/Conformance-Server:%2046%20%2F%2046%0A-green?logo=modelcontextprotocol)](https://github.com/modelcontextprotocol/conformance)
[![JVM](https://img.shields.io/badge/JVM-21+-orange.svg?logo=jvm)](http://java.com)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/kpavlov/tachyon)

**Tachyon MCP** — A Java 21 [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server built on [Netty 4.2](https://netty.io).
Fully implements MCP spec **2025-11-25** Streamable HTTP transport, session lifecycle, native I/O transports, and a stateless mode for serverless deployments.

**TL;DR**

```java
import dev.tachyonmcp.server.TachyonMcpServer;
import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import tools.jackson.databind.node.JsonNodeFactory;

void main() {
    var schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.putObject("properties").putObject("city").put("type", "string");

    TachyonMcpServer.builder()
        .name("weather-mcp")
        .tool(new AbstractSyncToolHandler(
            ToolDescriptor.builder("get_forecast")
                .description("Get weather forecast")
                .inputSchema(schema)
                .build()) {
            @Override
            public Object handle(McpContext ctx, Object args) {
                return ToolResult.text("☀️ 22°C");
            }
        })
        .stateless(true)
        .port(8080)
        .bind();
}
```

## Features

### Core Protocol (46/46 conformance tests passing)

- JSON-RPC 2.0 — request/response/error/notification
- Streamable HTTP — POST, GET (SSE), DELETE, OPTIONS
- Lifecycle — initialize → initialized → ACTIVE
- Pagination — cursor-based across all list methods
- Session state machine — INITIALIZING → ACTIVE → CLOSED
- CORS & origin validation
- DNS rebinding protection
- Accept header strict validation (406)
- Pending request timeout (60s)
- SSE resumability via Last-Event-ID

### Tools

- `tools/list` — paginated with `nextCursor`
- `tools/call` — returns `CallToolResult` with `isError`
- `outputSchema` in listing
- `annotations` field
- `execution.taskSupport` (forbidden/optional/required)
- Synchronous & asynchronous handler interfaces
- Name validation (1–128 chars)
- `notifications/tools/list_changed` on add/remove
- Inline notifications + logging during tool call
- Input schema validation

### Resources

- `resources/list` — paginated
- `resources/read` — text & blob content
- `resources/templates/list` — URI templates
- `resources/subscribe` / `unsubscribe`
- `notifications/resources/list_changed`
- `notifications/resources/updated` to subscribers
- Dynamic content via `ResourceHandler` interface

### Prompts

- `prompts/list` — paginated with `nextCursor`
- `prompts/get` — invokes prompt resolver
- `notifications/prompts/list_changed`

### Tasks

- `tasks/list`, `tasks/get`, `tasks/cancel`, `tasks/result`
- State machine enforcement — SUBMITTED → WORKING → COMPLETED/FAILED/CANCELLED
- `notifications/tasks/status` broadcast on every transition
- Task Janitor for stale tasks
- `execution.taskSupport` per tool (forbidden/optional/required)
- `TasksExtension` ([SEP-1686](https://modelcontextprotocol.io/seps/1686-tasks)) — negotiable extension exposing `create_task` tool + `task://{id}` resource template
- Extension-gated tool visibility (hidden from un-negotiated clients)

### Logging & Observability

- `logging/setLevel` per session
- `notifications/message` emitted above threshold
- Progress notifications

### Client Communication

- `sampling/createMessage` — server → client request
- Elicitation — ✅ form mode; ❌ url mode
- `notifications/cancelled` — bidirectional
- `notifications/tasks/status` from client

### Transport & I/O

- Netty 4.2
- io_uring / epoll / kqueue / nio auto-detection
- Platform-thread event loops + virtual-thread handlers
- TCP_NODELAY, SO_KEEPALIVE
- Channel writability backpressure (`setAutoRead`)
- Configurable idle timeouts (reader/writer)

### Session Management

- **Stateless mode** — skip sessions for serverless
- IN_MEMORY session store (ConcurrentHashMap)
- Session Janitor — 5s sweep, 30s TTL
- SSE disconnect ≠ session removal (supports reconnect)
- Event log replay on reconnection

---

## Installation

**Requirements**: JDK 21+

### Maven

```xml
<dependency>
    <groupId>dev.tachyonmcp</groupId>
    <artifactId>tachyon-server</artifactId>
    <version>1.0.0-alpha.2</version>
</dependency>
```

### Build from source
```bash
git clone https://github.com/kpavlov/tachyon.git
cd tachyon
mvn install -pl tachyon-mcp-server -DskipTests
```

## Quick Start

### Minimal server with tool

```java
import dev.tachyonmcp.server.TachyonMcpServer;
import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import tools.jackson.databind.node.JsonNodeFactory;

void main() {
    var schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.putObject("properties").putObject("city").put("type", "string");

    TachyonMcpServer.builder()
        .name("weather-mcp")
        .tool(new AbstractSyncToolHandler(
            ToolDescriptor.builder("get_forecast")
                .description("Get weather forecast")
                .inputSchema(schema)
                .build()) {
            @Override
            public Object handle(McpContext ctx, Object args) {
                return ToolResult.text("☀️ 22°C");
            }
        })
        .stateless(true) // start in stateless node (no sessions)
        .port(8080) // bind to 1270.0.1:8080
        .bind();
}
```

### With TasksExtension (negotiable) - [SEP-1686](https://modelcontextprotocol.io/seps/1686-tasks)

```java
var handle = TachyonMcpServer.builder()
    .extension(new TasksExtension())   // exposes create_task tool + task://{id} resource
    .port(8080)
    .bind();
```

Clients that include `"extensions": {"io.modelcontextprotocol/tasks": {}}` in their
`initialize` capabilities receive the extension's tool and resource template.
Clients that don't negotiate the extension see standard MCP tasks via `tasks/list` / `tasks/get`.

### Protocol isolation

Handler interfaces (`ToolHandler`, `ResourceHandler`, `PromptHandler`) and descriptor types use stable domain types.
When Tachyon upgrades to a new protocol version, only the internal mapper layer changes;
handler implementations are unaffected. Domain types track the 2026-07-28 spec shape where it improves on 2025-11-25 (e.g. `Annotations.lastModified`, `ResourceLink` in `ContentBlock`).

## Performance

- **Native transports** — io_uring > epoll > kqueue > NIO auto-detect
- **Write buffer watermarks** — 32 KB low / 128 KB high, backpressure wired
- **Batch flushing** — `ctx.write()` accumulates, single `ctx.flush()` on boundary
- **Minimal allocations** — `McpEndpointHandler` is `@Sharable`, no per-request handler creation
- **Virtual threads** — handlers offloaded from event loop, no manual thread pools
- **JSON-RPC** — Jackson streaming codec, no ObjectMapper.

## Gaps & Limitations

- [ ] **Rate limiting** — Not yet implemented
- [ ] **URL elicitation mode / -32042 error**  — Form mode works, URL mode missing
- [ ] **2026-07-28 draft protocol version** — Not negotiable; version-gated features ready
- [ ] **Stale session on re-initialize** — 30s TTL lingering, affects reconnect only

---

## FAQ

### Can I deploy to AWS Lambda?
Yes. Use `stateless(true)` to skip session persistence. Each invocation processes one request independently.

### Does it support HTTP/2?
Not yet. The current pipeline targets HTTP/1.1; HTTP/2 upgrade is a pipeline configuration change comming soon.

### How do I write a custom tool?
Extend `AbstractSyncToolHandler` or `AbstractAsyncToolHandler`, passing a `ToolDescriptor`:

```java
class MyTool extends AbstractSyncToolHandler {
    MyTool() {
        super(ToolDescriptor.builder("my_tool")
                .description("Does something useful")
                .inputSchema(buildSchema())
                .build());
    }

    @Override
    public Object handle(McpContext ctx, Object args) throws Exception {
        return CallToolResult.ofText("done");
    }

    private static JsonNode buildSchema() {
        var s = JsonNodeFactory.instance.objectNode();
        s.put("type", "object");
        return s;
    }
}
```

### How do I implement a custom resource?
```java
server.resources().add(
    ResourceDescriptor.of("custom://data"),
    (ctx, req) -> new TextResourceContents("content", req.uri(), "text/plain", null));
```


## License

**Tachyon MCP** is available under the terms of the [Apache 2.0](LICENSE).
