[![Maven Central](https://img.shields.io/maven-central/v/dev.tachyonmcp/tachyon-server)](https://repo1.maven.org/maven2/dev/tachyonmcp/tachyon-server/)
[![Java 21+](https://img.shields.io/badge/Java-21+-orange.svg?logo=jvm)](http://java.com)
[![Build](https://github.com/kpavlov/tachyon/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/kpavlov/tachyon/actions/workflows/build.yml)
[![Conformance: v0.1.16](https://img.shields.io/badge/Conformance-v0.1.16-green?logo=modelcontextprotocol)](https://github.com/modelcontextprotocol/conformance)
[![codecov](https://codecov.io/gh/kpavlov/tachyon/graph/badge.svg?token=WUMD9A8T2T)](https://codecov.io/gh/kpavlov/tachyon)
[![Docs](https://img.shields.io/badge/Docs-blue?logo=github)](https://github.com/kpavlov/tachyon/blob/main/docs/README.md)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/kpavlov/tachyon)
<img referrerpolicy="no-referrer-when-downgrade" src="https://static.scarf.sh/a.png?x-pxid=fd923998-7054-4524-b014-cd368cfba9fc" />

**Tachyon MCP** is a Java 21 [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server built on [Netty](https://netty.io). Implements the **2025-11-25** Streamable HTTP transport, protocol extensions, and stateless mode.

## 💫 Why Tachyon?

✅ **MCP spec compliant** -- Passes all official conformance tests for the **2025-11-25** specification, including tools, resources, prompts, tasks, elicitation, sampling, and extensions (SEP-1686, SEP-2133).

🛡️ **Stable APIs across spec changes** -- MCP evolves quickly. Stable domain APIs (`ToolHandler`, `ResourceHandler`, `PromptHandler`, task support) isolate protocol changes behind an internal mapping layer, so upgrades rarely affect application code.

🧵 **Synchronous code, asynchronous runtime** -- Write blocking handlers while Java 21 virtual threads run them off the Netty event loop. No thread pools, reactive pipelines, or `CompletableFuture` boilerplate. Includes a coroutine-first Kotlin DSL.

☁️ **Serverless by default** -- Stateless request handling works out of the box for AWS Lambda and similar platforms. Enable sessions (`.session(s -> s.enabled(true))`) for SSE resumability, `Last-Event-ID` replay, TTL cleanup, and customizable session IDs.

🚄 **Production-ready transport** — Built on Netty with backpressure, graceful shutdown, DNS rebinding protection, and automatic native transport support (`io_uring`, `epoll`, `kqueue`) where available.

## TL;DR

1. Add dependency:

    ```xml
    <dependency>
        <groupId>dev.tachyonmcp</groupId>
        <artifactId>tachyon-server</artifactId>
        <version>1.0.0-beta.7</version>
    </dependency>
    ```

2. Create MCP server:

    ```java
    import dev.tachyonmcp.server.TachyonServer;
    import dev.tachyonmcp.runtime.InteractionContext;
    import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
    import dev.tachyonmcp.server.features.tools.ToolArgs;
    import dev.tachyonmcp.server.features.tools.ToolDescriptor;
    import dev.tachyonmcp.server.features.tools.ToolResult;

    void main() {
        TachyonServer.builder()
            .name("weather-mcp")
            .tool(new AbstractSyncToolHandler(
                ToolDescriptor.builder("get_forecast")
                    .description("Get weather forecast")  
                    .inputSchema("""
                    {"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}
                    """)
                    .build()) {

                @Override
                public ToolResult handle(InteractionContext ctx, ToolArgs args) {
                    return ToolResult.text("☀️ 22°C");
                }
            })
            .port(8080)
            .start();
    }
    ```

## Documentation

| Guide | Description |
|---|---|
| [Quickstart](docs/quickstart.md) | Build a working server in 5 minutes |
| [Configuration](docs/configuration.md) | Network, I/O engine (native transports), sessions, CORS |
| [Tools](docs/tools.md) | Sync/async handlers, input schema, `ToolResult` |
| [Resources](docs/resources.md) | Static URIs, dynamic handlers, URI templates |
| [Tasks](docs/tasks.md) | Long-running operations, state machine, `TasksExtension` |
| [Extensions](docs/extensions.md) | Custom protocol extensions, negotiation |
| [Kotlin DSL](docs/kotlin.md) | Coroutine-first DSL, `TachyonServer { }`, scope reference |
| [Kotlin module](tachyon-server-kotlin/README.md) | `tachyon-server-kotlin` module overview |

## Agent Skill

Add agent skill to write better code using this SDK:

```shell
npx skills add kpavlov/tachyon --skill tachyon-mcp
```

The skill includes compilable Java and Kotlin example sources under `.agents/skills/tachyon-mcp/resources/`.
They are compiled as extra source roots of the `e2e` module during `mvn test` to keep them valid.

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
- Graceful shutdown — drains in-flight handlers for `shutdownGracePeriod` (default 5s) before force-interrupt

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

See [docs/quickstart.md](docs/quickstart.md) for a full walkthrough with Java and Kotlin examples, curl test, and next-step links.

### TasksExtension (SEP-1686)

```java
var handle = TachyonServer.builder()
    .extension(TasksExtension.instance())  // exposes create_task tool + task://{id} resource
    .port(8080)
    .start();
```

Clients that include `"extensions": {"io.modelcontextprotocol/tasks": {}}` in their `initialize` capabilities receive the extension's tool and resource template. Clients that don't negotiate it see standard `tasks/*` methods. See [docs/tasks.md](docs/tasks.md).

### Protocol isolation

Handler interfaces (`ToolHandler`, `ResourceHandler`, `PromptHandler`) and descriptor types use stable domain types. When Tachyon upgrades to a new protocol version, only the internal mapper layer changes; handler implementations are unaffected. Domain types track the 2026-07-28 spec shape where it improves on 2025-11-25 (e.g. `Annotations.lastModified`, `ResourceLink` in `ContentBlock`).

## Performance

- **Native transports** — io_uring > epoll > kqueue > NIO auto-detect
- **Write buffer watermarks** — 32 KB low / 128 KB high, backpressure wired
- **Batch flushing** — `ctx.write()` accumulates, single `ctx.flush()` on boundary
- **Minimal allocations** — `McpEndpointHandler` is `@Sharable`, no per-request handler creation
- **Virtual threads** — handlers offloaded from event loop, no manual thread pools
- **JSON-RPC** — Jackson streaming codec, no ObjectMapper.

## Gaps & Limitations

- [ ] **Rate limiting** — Not yet implemented
- [ ] **2026-07-28 draft protocol version** — High priority
- [ ] **Stale session on re-initialize** — 30s TTL lingering, affects reconnect only

---

## FAQ

### Can I deploy to AWS Lambda?
Yes. Servers are stateless by default, so each invocation processes one request independently. Enable sessions with `.session(cfg -> cfg.enabled(true))` when you need SSE resumability or replay.

### Does it support HTTP/2?
Not yet. The current transport targets HTTP/1.1.

### How do I write a tool?
See [docs/tools.md](docs/tools.md) — covers lambda and class-based handlers, input schema, and `ToolResult` factories.

### How do I expose a resource?
See [docs/resources.md](docs/resources.md) — covers static URIs, dynamic handlers, URI templates, and subscriptions.

## License

**Tachyon MCP** is available under the terms of the [Apache 2.0](LICENSE).
