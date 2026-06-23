

[![Maven Central](https://img.shields.io/maven-central/v/dev.tachyonmcp/tachyon-server)](https://repo1.maven.org/maven2/dev/tachyonmcp/tachyon-server/)
[![Build](https://github.com/kpavlov/tachyon/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/kpavlov/tachyon/actions/workflows/build.yml)
[![Conformance 46 of 46](https://img.shields.io/badge/Conformance-Server:%2046%20%2F%2046%0A-green?logo=modelcontextprotocol)](https://github.com/modelcontextprotocol/conformance)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/kpavlov/tachyon)

**Tachyon MCP** — A Java 25 [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server built on [Netty 4.2](https://netty.io).
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

### ✅ Core Protocol (46/46 conformance tests passing)

- [x] JSON-RPC 2.0 — request/response/error/notification
- [x] Streamable HTTP — POST, GET (SSE), DELETE, OPTIONS
- [x] Lifecycle — initialize → initialized → ACTIVE
- [x] Pagination — cursor-based across all list methods
- [x] Session state machine — INITIALIZING → ACTIVE → CLOSED
- [x] CORS & origin validation
- [x] DNS rebinding protection
- [x] Accept header strict validation (406)
- [x] Pending request timeout (60s)
- [x] SSE resumability via Last-Event-ID

### ✅ Tools

- [x] `tools/list` — paginated with `nextCursor`
- [x] `tools/call` — returns `CallToolResult` with `isError`
- [x] `outputSchema` in listing
- [x] `annotations` field
- [x] `execution.taskSupport` (forbidden/optional/required)
- [x] Synchronous & asynchronous handler interfaces
- [x] Name validation (1–128 chars)
- [x] `notifications/tools/list_changed` on add/remove
- [x] Inline notifications + logging during tool call

### ✅ Resources

- [x] `resources/list` — paginated
- [x] `resources/read` — text & blob content
- [x] `resources/templates/list` — URI templates
- [x] `resources/subscribe` / `unsubscribe`
- [x] `notifications/resources/list_changed`
- [x] `notifications/resources/updated` to subscribers
- [x] Dynamic content via `ResourceHandler` interface

### ✅ Prompts

- [x] `prompts/list` — paginated with `nextCursor`
- [x] `prompts/get` — invokes prompt resolver
- [x] `notifications/prompts/list_changed`

### ✅ Tasks

- [x] `tasks/list`, `tasks/get`, `tasks/cancel`, `tasks/result`
- [x] State machine enforcement — SUBMITTED → WORKING → COMPLETED/FAILED/CANCELLED
- [x] `notifications/tasks/status` broadcast on every transition
- [x] Task Janitor for stale tasks
- [x] `execution.taskSupport` per tool (forbidden/optional/required)
- [x] `TasksExtension` — negotiable extension exposing `create_task` tool + `task://{id}` resource template
- [x] Extension-gated tool visibility (hidden from un-negotiated clients)

### ✅ Logging & Observability

- [x] `logging/setLevel` per session
- [x] `notifications/message` emitted above threshold
- [x] Progress notifications

### ✅ Client Communication

- [x] `sampling/createMessage` — server → client request
- [x] Elicitation — form mode
- [x] `notifications/cancelled` — bidirectional
- [x] `notifications/tasks/status` from client

### ✅ Transport & I/O

- [x] Netty 4.2
- [x] io_uring / epoll / kqueue / nio auto-detection
- [x] Platform-thread event loops + virtual-thread handlers
- [x] TCP_NODELAY, SO_KEEPALIVE
- [x] Channel writability backpressure (`setAutoRead`)
- [x] Configurable idle timeouts (reader/writer)

### ✅ Session Management

- [x] **Stateless mode** — skip sessions for serverless
- [x] IN_MEMORY session store (ConcurrentHashMap)
- [x] Session Janitor — 5s sweep, 30s TTL
- [x] SSE disconnect ≠ session removal (supports reconnect)
- [x] Event log replay on reconnection

---

## Installation

**Requirements**: JDK 25+

### Maven
```xml
<dependency>
    <groupId>dev.tachyonmcp</groupId>
    <artifactId>tachyon-server</artifactId>
    <version>1.0.0-alpha.1</version>
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

### With TasksExtension (negotiable)
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

- [ ] **Rate limiting** (Medium) — Not yet implemented
- [ ] **URL elicitation mode / -32042 error** (Medium) — Form mode works, URL mode missing
- [ ] **2026-07-28 draft protocol version** (Low) — Not negotiable; version-gated features ready
- [ ] **Stale session on re-initialize** (Low) — 30s TTL lingering, affects reconnect only

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
