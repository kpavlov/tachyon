<div>
</div>

[![Maven Central](https://img.shields.io/maven-central/v/dev.tachyonmcp/tachyon-server)](https://repo1.maven.org/maven2/dev/tachyonmcp/tachyon-server/)
[![Java 21+](https://img.shields.io/badge/Java-21+-orange.svg?logo=jvm)](http://java.com)
[![Build](https://github.com/kpavlov/tachyon/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/kpavlov/tachyon/actions/workflows/build.yml)
[![Conformance: v0.1.16](https://img.shields.io/badge/Conformance-v0.1.16-green?logo=modelcontextprotocol)](https://github.com/modelcontextprotocol/conformance)
[![codecov](https://codecov.io/gh/kpavlov/tachyon/graph/badge.svg?token=WUMD9A8T2T)](https://codecov.io/gh/kpavlov/tachyon)

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/kpavlov/tachyon)

**Tachyon MCP** is a Java 21 [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server built on [Netty](https://netty.io).
Fully implements MCP spec **2025-11-25** Streamable HTTP transport with native I/O, protocol extensions, and a stateless mode for serverless deployments.

**TL;DR**

1. Add dependency:

    ```xml
    <dependency>
        <groupId>dev.tachyonmcp</groupId>
        <artifactId>tachyon-server</artifactId>
        <version>1.0.0-beta.2</version>
    </dependency>
    ```

2. Create MCP server:

    ```java
    import dev.tachyonmcp.server.TachyonServer;
    import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
    import dev.tachyonmcp.server.features.tools.ToolDescriptor;
    import dev.tachyonmcp.server.features.tools.ToolResult;
    import dev.tachyonmcp.server.session.McpContext;
    import tools.jackson.databind.node.JsonNodeFactory;

    void main() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject("city").put("type", "string");

        TachyonServer.builder()
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
            .session(cfg -> cfg.stateless(true))
            .port(8080)
            .bind();
    }
    ```

## Agent Skill

Add agent skill to write better code using this SDK:

```shell
npx skills add kpavlov/tachyon --skill tachyon-mcp
```

Check out [Skills CLI](https://github.com/vercel-labs/skills) for more options.

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
- Extensions ([SEP-2133](https://modelcontextprotocol.io/seps/2133-extensions))

### Tools

- `tools/list` — paginated with `nextCursor`
- `tools/call` — returns `CallToolResult` with `isError`
- `outputSchema` in listing
- `annotations` field
- `execution.taskSupport` (forbidden/optional/required)
- Synchronous & asynchronous handler interfaces
- Tool name validation ([SEP-986](https://modelcontextprotocol.io/seps/986-specify-format-for-tool-names))
- `notifications/tools/list_changed` on add/remove
- Inline notifications + logging during tool call
- Input JSON Schema 2020-12 validation ([SEP-1613](https://modelcontextprotocol.io/seps/1613-establish-json-schema-2020-12-as-default-dialect-f))

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
- State machine enforcement — SUBMITTED → WORKING → INPUT_REQUIRED → COMPLETED/FAILED/CANCELLED, plus REJECTED/AUTH_REQUIRED
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

- SSE resumability via Last-Event-ID
- **Stateless mode** — skip sessions for serverless
- IN_MEMORY session store (ConcurrentHashMap)
- Session Janitor — 5s sweep, 30s TTL
- SSE disconnect ≠ session removal (supports reconnect)
- Event log replay on reconnection

---

## Installation

**Requirements**: JDK 21+

### Build from source
```bash
git clone https://github.com/kpavlov/tachyon.git
cd tachyon
mvn install -pl tachyon-server -DskipTests
```

## Quick Start

### Minimal server with tool

```java
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import tools.jackson.databind.node.JsonNodeFactory;

void main() {
    var schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.putObject("properties").putObject("city").put("type", "string");

    TachyonServer.builder()
        .name("weather-mcp")
        .tool(new AbstractSyncToolHandler<ToolResult>(
            ToolDescriptor.builder("get_forecast")
                .description("Get weather forecast")
                .inputSchema(schema)
                .build()) {
            @Override
            public ToolResult handle(McpContext ctx, @Nullable Map<String, JsonNode> args) {
                return ToolResult.text("☀️ 22°C");
            }
        })
        .session(cfg -> cfg.stateless(true)) // start in stateless mode (no sessions)
        .port(8080) // bind to 127.0.0.1:8080
        .bind();
}
```

### With TasksExtension (negotiable) - [SEP-1686](https://modelcontextprotocol.io/seps/1686-tasks)

```java
var handle = TachyonServer.builder()
    .extension(TasksExtension.instance())  // exposes create_task tool + task://{id} resource
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
class MyTool extends AbstractSyncToolHandler<ToolResult> {
    MyTool() {
        super(ToolDescriptor.builder("my_tool")
                .description("Does something useful")
                .inputSchema(buildSchema())
                .build());
    }

    @Override
    public ToolResult handle(McpContext ctx, @Nullable Map<String, JsonNode> args) throws Exception {
        return ToolResult.text("done");
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
    ResourceDescriptor.of("custom-data", "custom://data", null, null),
    (ctx, req) -> new TextResourceContents("content", req.uri(), "text/plain", null));
```


## License

**Tachyon MCP** is available under the terms of the [Apache 2.0](LICENSE).
