[![Maven Central](https://img.shields.io/maven-central/v/dev.tachyonmcp/tachyon-core)](https://central.sonatype.com/search?q=dev.tachyonmcp%2Ftachyon-*)
[![Java 21+](https://img.shields.io/badge/Java-21+-orange.svg?logo=jvm)](http://java.com)
[![Build](https://github.com/kpavlov/tachyon/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/kpavlov/tachyon/actions/workflows/build.yml)
[![MCPConformance: 2025-11-25+2026-07-28](https://img.shields.io/badge/MCP%20Conformance-2025.11.25%20+%202026.07.28-grass?logo=modelcontextprotocol)](https://github.com/modelcontextprotocol/conformance)
[![codecov](https://codecov.io/gh/kpavlov/tachyon/graph/badge.svg?token=WUMD9A8T2T)](https://codecov.io/gh/kpavlov/tachyon)
[![Docs](https://img.shields.io/badge/Docs-blue?logo=github)](https://github.com/kpavlov/tachyon/blob/main/docs/README.md)
[![Api](https://img.shields.io/badge/API-blue?logo=github)](https://kpavlov.github.io/tachyon/apidocs/)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/kpavlov/tachyon)

<div style="align-content: center">
  <img
    src="docs/assets/social-banner.jpg"
    alt="Tachyon MCP banner"
    style="width: 100%; height: auto;border-radius: 10px"
  />
</div>

**Tachyon MCP** is a production [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server runtime for Java.

Runs on Java 21+ with [Netty](https://netty.io), virtual threads, Kotlin API. No framework required. Implements **MCP 2025-11-25** and **MCP 2026-07-28** over Streamable HTTP and runs stateless by default. **It passes all official conformance tests for both protocol versions!**

## 💫 Why Tachyon?

🧵 **Synchronous code, asynchronous runtime** — write blocking handlers; Java virtual threads run them off the Netty event loop. No thread pools, reactive pipelines, or `CompletableFuture` boilerplate. Coroutine-first Kotlin DSL included.

🛡️ **Stable APIs across spec changes** — domain types (`ToolFn`, `ResourceFn`, `PromptFn`, tasks) sit behind an internal protocol mapper. Spec upgrades change the mapper, not your handlers.

☁️ **Stateless by default** — scale without session affinity on containers and serverless runtimes.
AWS Lambda needs an adapter that supports a listening HTTP server and long-lived SSE responses.
Opt into sessions for SSE resumability, `Last-Event-ID` replay, and TTL cleanup.

🚄 **Production transport** — Netty with backpressure, graceful shutdown, DNS rebinding protection, and native transport auto-detection (`io_uring` → `epoll` → `kqueue` → NIO).

## TL;DR

1. Add dependency:

    ```xml
    <dependency>
        <groupId>dev.tachyonmcp</groupId>
        <artifactId>tachyon-core</artifactId>
        <version>1.0.0-beta.19</version>
    </dependency>
    ```

2. Create MCP server:

    ```java
    import dev.tachyonmcp.core.server.TachyonServer;
    import dev.tachyonmcp.api.server.features.tools.ToolResult;

    public class WeatherMcpServer {
        public static void main(String... args) {
            var server = TachyonServer.builder()
                .name("weather-mcp")
                .withTools(tools -> tools.register(
                        b -> b.name("get_forecast")
                            .description("Get weather forecast")
                            .inputSchema("""
                            {"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}
                            """),
                        (ctx, request) -> ToolResult.text("☀️ 22°C")))
                .port(8080)
                .build();
            server.start();
        }
    }
    ```

## Documentation

- [Quickstart](docs/quickstart.md) - Build a working server in 5 minutes
- [Configuration](docs/configuration.md) - Network, I/O engine (native transports), sessions, CORS
- [Tools](docs/tools.md) - Sync/async handlers, input schema, `ToolResult`
- [Resources](docs/resources.md) - Static URIs, dynamic handlers, URI templates
- [Tasks](docs/tasks.md) - Long-running operations, state machine, `TasksExtension`
- [Extensions](docs/extensions.md) - Custom protocol extensions, negotiation
- [FAQ](docs/faq.md) - Java, frameworks, concurrency, deployment, and compatibility
- [Kotlin DSL](docs/kotlin.md) - Coroutine-first DSL, `TachyonServer { }`, scope reference
- [Kotlin module](tachyon-kotlin/README.md) - `tachyon-kotlin` module overview
- [Testkit](tachyon-testkit/README.md) - Test harness: shaping clients, dynamic-port servers
- [Examples](examples/README.md) - Runnable Java & Kotlin example servers
- [Migrate from Kotlin MCP SDK](docs/migrate-from-kotlin-mcp-to-tachyon.md) - Moving an existing Kotlin MCP SDK server
  to Tachyon

## Agent Skill

Add agent skill to write better code using this SDK:

```shell
npx skills add kpavlov/tachyon --skill tachyon-mcp
```

The skill includes compilable Java and Kotlin example sources under `.agents/skills/tachyon-mcp/resources/`.
They are compiled as extra source roots of the `e2e` module during `mvn test` to keep them valid.

Check out [Skills CLI](https://github.com/vercel-labs/skills) for more options.

## Features

Full MCP surface over Streamable HTTP, verified by the official conformance suites for **2025-11-25** and **2026-07-28**:

The wire lifecycle varies by negotiated version; the detailed method list below describes the
2025-11-25 surface unless noted otherwise.

| Area | What you get |
|---|---|
| **Tools** | Sync & async handlers, JSON Schema 2020-12 input/output validation, `outputSchema`, annotations, per-tool `taskSupport`, `list_changed` |
| **Resources** | Static + dynamic handlers, URI templates, subscribe/unsubscribe with `updated` notifications, text & blob content |
| **Prompts** | List/get with resolver handlers, input-required (MRTR) flow, `list_changed` |
| **Tasks** | Full `tasks/*` lifecycle with enforced state machine, status broadcast, stale-task janitor, `TasksExtension` (SEP-1686) |
| **Client calls** | `sampling/createMessage`, elicitation (form **and** URL modes), client-initiated `cancelled` |
| **Sessions** | Stateless by default; opt-in SSE resumability, `Last-Event-ID` replay, TTL janitor, pluggable store/ID generator |
| **Transport** | Native transport auto-detect, backpressure watermarks, graceful drain-on-shutdown, CORS + origin/DNS-rebinding protection |
| **Extensions** | Negotiable protocol extensions (SEP-2133) with extension-gated tool visibility |

<details>
<summary>Detailed method-by-method breakdown</summary>

**Core** — JSON-RPC 2.0; Streamable HTTP (POST/GET-SSE/DELETE/OPTIONS); lifecycle `initialize → initialized → ACTIVE`; cursor pagination on all list methods; strict `Accept` validation (406); pending-request timeout.

**Tools** — `tools/list` (paginated), `tools/call` (`isError`), `outputSchema` + `annotations`, sync/async handlers, name validation ([SEP-986](https://modelcontextprotocol.io/seps/986-specify-format-for-tool-names)), inline notifications/logging mid-call, JSON Schema 2020-12 validation ([SEP-1613](https://modelcontextprotocol.io/seps/1613-establish-json-schema-2020-12-as-default-dialect-f)), `notifications/tools/list_changed`.

**Resources** — `resources/list`, `resources/read` (text & blob), `resources/templates/list`, `subscribe`/`unsubscribe`, `list_changed` + `updated` notifications, dynamic `ResourceFn`.

**Prompts** — `prompts/list` (paginated), `prompts/get`, input-required flow, `list_changed`.

**Tasks** — `tasks/list|get|cancel|result`; state machine `SUBMITTED → WORKING → INPUT_REQUIRED → COMPLETED/FAILED/CANCELLED` (+ `REJECTED`/`AUTH_REQUIRED`); `notifications/tasks/status` on every transition; stale-task janitor; per-tool `execution.taskSupport`; `TasksExtension` ([SEP-1686](https://modelcontextprotocol.io/seps/1686-tasks)) exposing `create_task` + `task://{id}`, hidden from clients that don't negotiate it.

**Logging & client calls** — `logging/setLevel` per session, `notifications/message` above threshold, progress notifications; `sampling/createMessage`; elicitation form + URL modes; client-initiated `notifications/cancelled`.

**Transport & sessions** — Netty 4.2, `io_uring`/`epoll`/`kqueue`/NIO auto-detect, platform-thread event loops + virtual-thread handlers, writability backpressure, configurable idle timeouts; stateless or in-memory sessions, 5s janitor / 30s TTL, SSE disconnect survives (event-log replay on reconnect), graceful drain (`shutdownGracePeriod`, default 5s) before force-interrupt.

</details>

---

## Quick Start

See [docs/quickstart.md](docs/quickstart.md) for a full walkthrough with Java and Kotlin examples, curl test, and next-step links.

### TasksExtension (SEP-1686)

```java
var server = TachyonServer.builder()
    .withExtensions(TasksExtension.instance())  // exposes create_task tool + task://{id} resource
    .port(8080)
    .build();
server.start();
```

MCP 2025-11-25 clients that include `"extensions": {"io.modelcontextprotocol/tasks": {}}` in their `initialize` capabilities receive the extension's tool and resource template. Clients that don't negotiate it see standard `tasks/*` methods. See [docs/tasks.md](docs/tasks.md).

### Protocol isolation

Function interfaces (`ToolFn`/`AsyncToolFn`, `ResourceFn`/`AsyncResourceFn`,
`PromptFn`/`AsyncPromptFn`, and `CompletionFn`/`AsyncCompletionFn`) and descriptor types use stable
domain types. Sync and async functions are independent contracts. When Tachyon upgrades to a new
protocol version, only the internal mapper layer changes.

## Performance

- **Native transports** — `io_uring` → `epoll` → `kqueue` → NIO auto-detect
- **Write-buffer watermarks** — 32 KB low / 128 KB high, backpressure wired end to end
- **Batch flushing** — `ctx.write()` accumulates, one `ctx.flush()` per boundary
- **Sharable handlers** — `@Sharable` pipeline handlers, no per-request allocation
- **Virtual threads** — handlers offloaded from the event loop, no manual pools
- **Streaming JSON-RPC** — Jackson streaming codec, no or limited `ObjectMapper` tree round-trips

## Not yet supported

- **HTTP/2** — transport is HTTP/1.1
- **Rate limiting**
- **Telemetry**

## License

**Tachyon MCP** is available under the terms of the [Apache 2.0](LICENSE).

<div style="display:flex; align-content:center; justify-content: center; width: 100%">
  <img
    src="docs/assets/logo-512.png"
    alt="Tachyon logo"
    style="display:inline-block; width: 256px; height: auto;"
  />
</div>


## [example] Add MCP SkillsExtension example project (Java)

Documentation reference and guidelines for #245.
