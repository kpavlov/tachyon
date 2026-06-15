
**Tachyon MCP** — A Java 25 [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server built on [Netty 4.2](https://netty.io).
Fully implements MCP spec **2025-11-25** Streamable HTTP transport, session lifecycle, native I/O transports, and a stateless mode for serverless deployments.

```java
TachyonMcpServer.builder()
    .name("weather-mcp")
    .tool(myWeatherTool)
    .port(8080)
    .bind();
```

## Features

### ✅ Core Protocol (46/46 conformance tests passing)

- ✅ JSON-RPC 2.0 — request/response/error/notification
- ✅ Streamable HTTP — POST, GET (SSE), DELETE, OPTIONS
- ✅ Lifecycle — initialize → initialized → ACTIVE
- ✅ Pagination — cursor-based across all list methods
- ✅ Session state machine — INITIALIZING → ACTIVE → CLOSED
- ✅ Backpressure — HOT/WARM/COLD with channel writability
- ✅ CORS & origin validation
- ✅ DNS rebinding protection
- ✅ Accept header strict validation (406)
- ✅ Protocol version negotiation
- ✅ Max request body 1 MB
- ✅ Pending request timeout (60s)
- ✅ SSE resumability via Last-Event-ID

### ✅ Tools

- ✅ `tools/list` — paginated with `nextCursor`
- ✅ `tools/call` — returns `CallToolResult` with `isError`
- ✅ `outputSchema` in listing
- ✅ `annotations` field
- ✅ `execution.taskSupport` (forbidden/optional/required)
- ✅ Synchronous & asynchronous handler interfaces
- ✅ Name validation (1–128 chars)
- ✅ Unknown tool → -32601
- ✅ `notifications/tools/list_changed` on add/remove
- ✅ Inline notifications + logging during tool call

### ✅ Resources

- ✅ `resources/list` — paginated
- ✅ `resources/read` — text & blob content
- ✅ `resources/templates/list` — URI templates
- ✅ `resources/subscribe` / `unsubscribe`
- ✅ `notifications/resources/list_changed`
- ✅ `notifications/resources/updated` to subscribers
- ✅ Not found → -32002
- ✅ Dynamic content via `ResourceHandler` interface

### ✅ Prompts

- ✅ `prompts/list` — paginated with `nextCursor`
- ✅ `prompts/get` — invokes prompt resolver
- ✅ `title` and `arguments` in listing
- ✅ `notifications/prompts/list_changed`
- ✅ Invalid name → error

### ✅ Tasks

- ✅ `tasks/list`, `tasks/get`, `tasks/cancel`, `tasks/result`
- ✅ State machine enforcement — SUBMITTED → WORKING → COMPLETED/FAILED/CANCELLED
- ✅ `notifications/tasks/status` broadcast on every transition
- ✅ TTL janitor for stale tasks
- ✅ `execution.taskSupport` per tool (forbidden/optional/required)
- ✅ `TasksExtension` — negotiable extension exposing `create_task` tool + `task://{id}` resource template
- ✅ Extension-gated tool visibility (hidden from un-negotiated clients)

### ✅ Logging & Observability

- ✅ `logging/setLevel` per session
- ✅ `notifications/message` emitted above threshold
- ✅ Progress notifications (0, 50, 100)

### ✅ Client Communication

- ✅ `sampling/createMessage` — server → client request
- ✅ Elicitation — form mode (SEP-1034, SEP-1330)
- ✅ `notifications/cancelled` — bidirectional
- ✅ `notifications/tasks/status` from client

### ✅ Transport & I/O

- ✅ Netty 4.2 — `ServerBootstrap` with pooled allocator
- ✅ io_uring / epoll / kqueue / nio auto-detection
- ✅ Platform-thread event loops + virtual-thread handlers
- ✅ Write buffer watermarks (32 KB / 128 KB)
- ✅ TCP_NODELAY, SO_KEEPALIVE
- ✅ Channel writability backpressure (`setAutoRead`)
- ✅ Configurable idle timeouts (reader/writer)

### ✅ Session Management

- ✅ IN_MEMORY session store (ConcurrentHashMap)
- ✅ Session janitor — 5s sweep, 30s TTL
- ✅ SSE disconnect ≠ session removal (supports reconnect)
- ✅ Event log replay on reconnection
- ✅ **Stateless mode** — skip sessions for serverless

---

## Installation

**Requirements**: JDK 25+

### Maven
```xml
<dependency>
    <groupId>dev.tachyonmcp</groupId>
    <artifactId>tachyon-mcp-server</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Build from source
```bash
git clone https://github.com/kpavlov/tachyon.git
cd tachyon
mvn install -pl tachyon-mcp-server -DskipTests
```

---

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

### Stateless mode (serverless)
```java
var handle = TachyonMcpServer.builder()
    .stateless(true)
    .tool(myTool)
    .port(8080)
    .bind();
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

---

## Builder API

```java
TachyonMcpServer.builder()
//IdentityStep
  .name() .version() .description() .websiteUrl()
  .toolsEnabled() .resourcesEnabled() .promptsEnabled()

//CapabilitiesStep / FeatureStep / SessionStep
  .stateless(boolean)      // ← skip session management
  .sessionTtl(Duration)    // ← janitor timeout (default 30s)
  .sessionLogRouter(...)   //  ← event log backend
  .sessionStore(...)       //  ← session storage backend

//FeatureStep
  .tool(ToolHandler)
  .resource(ResourceDescriptor[, ResourceHandler])
  .prompt(PromptDescriptor, PromptHandler)
  .task(TaskEntry)
  .extension(McpExtension)   // ← register negotiable extension

// NetworksStep
  .host(String).port(int)
  .endpointPath(String)   //   ← default "/mcp"
  .readerIdleTimeout(Duration)
  .writerIdleTimeout(Duration)

  .build() // → McpServer
  .bind()  //→ McpServerHandle
```

### Protocol isolation

Handler interfaces (`ToolHandler`, `ResourceHandler`, `PromptHandler`) and descriptor types use stable domain types in `me.kpavlov.tachyon.mcp.server.domain` — same names as the MCP spec, independent package. When Tachyon upgrades to a new protocol version, only the internal mapper layer changes; handler implementations are unaffected. Domain types track the 2026-07-28 spec shape where it improves on 2025-11-25 (e.g. `Annotations.lastModified`, `ResourceLink` in `ContentBlock`).

## Performance

- **Pooled ByteBuf allocator** — zero-copy buffer management
- **Native transports** — io_uring > epoll > kqueue > NIO auto-detect
- **Write buffer watermarks** — 32 KB low / 128 KB high, backpressure wired
- **Batch flushing** — `ctx.write()` accumulates, single `ctx.flush()` on boundary
- **Minimal allocations** — `McpEndpointHandler` is `@Sharable`, no per-request handler creation
- **Virtual threads** — handlers offloaded from event loop, no manual thread pools
- **JSON-RPC** — Jackson streaming codec

---

## Conformance

**46/46** tests pass on the official `@modelcontextprotocol/conformance@0.1.16` runner. Verified against MCP spec **2025-11-25**:

---

## Gaps & Limitations

- [ ] **Rate limiting** (Medium) — Not yet implemented
- [ ] **URL elicitation mode / -32042 error** (Medium) — Form mode works, URL mode missing
- [ ] **2026-07-28 draft protocol version** (Low) — Not negotiable; version-gated features ready
- [ ] **Invalid level → -32602** (Low) — Not tested; runtime behaviour is spec-compliant
- [ ] **Stale session on re-initialize** (Low) — 30s TTL lingering, affects reconnect only

---

## Configuration

### ServerConfig

- `sessionTtl`: `30s` — Session idle timeout for janitor sweep
- `stateless`: `false` — Skip session management for serverless

### Netty

- `host`: `127.0.0.1` — Bind address
- `port`: `-1` (required) — Listen port (0 = random)
- `endpointPath`: `/mcp` — HTTP path for MCP endpoint
- `readerIdleTimeout`: `60s` — Close idle connections
- `writerIdleTimeout`: `5min` — Close connections with no outbound data

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

---

## License

**Tachyon MCP** is available under the terms of the [Apache 2.0](LICENSE).
