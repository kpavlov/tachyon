# Configuration — Tachyon MCP Server

All configuration flows through `TachyonServer.builder()` (Java) or the `TachyonServer { }` DSL (Kotlin). Scopes: `info`, `capabilities`, `network`, `session`, `runtime`, `monitoring`.

```java
TachyonServer.builder()
    .info(i -> i.name("my-server").version("1.0"))
    .network(n -> n.port(8080).ioEngine(NettyIoEngine.AUTO))
    .session(s -> s.sessionTtl(Duration.ofMinutes(5)))
    .start();
```

```kotlin
val server = TachyonServer {
    info { name = "my-server"; version = "1.0" }
    network { port = 8080 }
    session { sessionTtl = 5.minutes }
}
```

## Network

Configured via `network { }` / `NetworkConfig.Builder`.

| Option | Default | Description |
|---|---|---|
| `host` | `127.0.0.1` | Bind address |
| `port` | — | Listen port; `0` picks a free port. Must be set before `start()` |
| `address` | — | Full `SocketAddress`; mutually exclusive with `host`/`port` |
| `endpointPath` | `/mcp` | HTTP endpoint path |
| `readerIdleTimeout` | `60s` | Close connections with no inbound traffic for this long |
| `writerIdleTimeout` | `5m` | Close connections with no outbound traffic for this long |
| `heartbeatInterval` | `15s` | SSE heartbeat interval for silent listening streams; `<= 0` disables |
| `maxContentLength` | `1 MB` | Max aggregated HTTP request body |
| `ioEngine` | `AUTO` | Netty I/O transport, see below |

`.port(int)` is also available as a top-level `ServerBuilder` shortcut.

### Keep-alive for long-running tools

```mermaid
sequenceDiagram
  participant McpClient
  participant TachyonServer
  participant LongRunningHandler

  McpClient->>TachyonServer: POST /mcp
  TachyonServer->>LongRunningHandler: invoke slow-progress tool
  LongRunningHandler->>TachyonServer: ctx.notifications().progress(...)/.comment()
  TachyonServer-->>McpClient: text/event-stream response
  TachyonServer-->>McpClient: SSE heartbeat comments
  LongRunningHandler->>TachyonServer: ToolResult.text("done")
  TachyonServer->>McpClient: tool-call result: "done"
```

`readerIdleTimeout` closes any connection that receives **no inbound bytes** for its duration.
After a client finishes sending a request it stays silent while waiting for the reply, so this
timer also runs while your handler is computing — a tool that takes longer than
`readerIdleTimeout` (default `60s`) to respond has its connection reaped before it can answer.

> [!NOTE]
> 💡 Set `readerIdleTimeout` to `Duration.ZERO` to disable closing connections on idle inbound stream.

The fix is not a bigger timeout — it is to keep the stream alive with **SSE + heartbeats**. Any
server→client message on the POST upgrades the response from buffered JSON to `text/event-stream`;
from then on the connection is a live SSE stream, `readerIdleTimeout` is a **no-op** on it, and a
fixed-rate scheduler emits a `:\r\n` comment every `heartbeatInterval` (default `15s`) so the
stream never looks idle. There are two ways to trigger the upgrade:

- **`progress(token, ...)`** — when the client requested progress (sent `_meta.progressToken`).
  Forward that token, exposed as `ToolRequest.progressToken()`. A `null` token is **silently
  dropped** per the MCP spec (the client didn't opt in) — no bytes are sent, so it does **not**
  upgrade the connection or keep it alive.
- **`comment(msg)`** — a token-free SSE comment (`: msg`). Use it to keep alive when no progress
  token is available, since a dropped `progress(null, ...)` sends nothing. `comment()` emits a
  bare `:` heartbeat.

The progress token is available only from `ToolRequest`, so override `handle(ctx, ToolRequest)` or
`handleAsync(ctx, ToolRequest)` when forwarding progress. `comment(...)` needs no token and is
available from any handler's `InteractionContext`:

```java
class SlowTool extends AbstractToolHandler {
    SlowTool() {
        super(ToolDescriptor.builder().name("slow-task").description("Long task, kept alive").build());
    }

    @Override
    public ToolResult handle(InteractionContext ctx, ToolRequest request) throws Exception {
        var token = request.progressToken();          // client _meta.progressToken; null if absent
        for (int i = 0; i < total; i++) {
            // First call upgrades POST → SSE and arms the heartbeat.
            if (token != null) {
                ctx.notifications().progress(token, i, total, "step " + i);
            } else {
                ctx.notifications().comment("step " + i);   // token-free keep-alive
            }
            doSlowStep(i);
        }
        return ToolResult.text("done");
    }
}
```

Guidance:

- No token available? Use `comment(...)` — it upgrades and keeps the stream alive without one.
  A `null`-token `progress(...)` call is silently dropped (no throw, no bytes sent, no upgrade).
- Keep `heartbeatInterval < readerIdleTimeout` (default `15s < 60s`) so a live stream's own
  heartbeats always beat the reader-idle deadline.
- Size `readerIdleTimeout` for **dead-peer detection** (how long a silent connection may live),
  not for how long a tool runs. Bumping it to cover a slow tool is the wrong lever — use the SSE
  keep-alive pattern above.
- `heartbeatInterval <= 0` disables heartbeats; silent SSE streams then close on idle. Lower it
  below any proxy/load-balancer idle timeout sitting in front of the server.

### CORS

| Option | Default | Description |
|---|---|---|
| `allowedOrigins` | — | Allowed `Origin` values; unset disables CORS handling |
| `allowNullOrigin` | `false` | Allow `Origin: null` |
| `allowPrivateNetworks` | `false` | Allow private-network CORS preflight |
| `allowedHeaders` | — | Extra allowed request headers |

## I/O engine

`NettyIoEngine` selects the Netty transport:

| Engine | OS | Runtime dependency |
|---|---|---|
| `AUTO` (default) | any | picks best available, in order below |
| `IO_URING` | Linux 5.9+ | `netty-transport-native-io_uring` |
| `EPOLL` | Linux | `netty-transport-native-epoll` |
| `KQUEUE` | macOS / BSD | `netty-transport-native-kqueue` |
| `NIO` | any | none (bundled) |

`AUTO` detection order: io_uring → epoll → kqueue → NIO. Detection happens once and is cached; the chosen engine is logged at startup:

```text
Netty I/O engine: KQUEUE
```

Native transports are optional runtime dependencies — `tachyon-server` itself depends only on the portable NIO transport. To enable a native transport, add the matching jar with your platform classifier (via [os-maven-plugin](https://github.com/trustin/os-maven-plugin)):

```xml
<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
</build>

<profiles>
    <profile>
        <id>netty-native-linux</id>
        <activation>
            <os><name>linux</name></os>
        </activation>
        <dependencies>
            <dependency>
                <groupId>io.netty</groupId>
                <artifactId>netty-transport-native-epoll</artifactId>
                <version>${netty.version}</version>
                <classifier>${os.detected.classifier}</classifier>
                <scope>runtime</scope>
            </dependency>
        </dependencies>
    </profile>
    <profile>
        <id>netty-native-mac</id>
        <activation>
            <os><family>mac</family></os>
        </activation>
        <dependencies>
            <dependency>
                <groupId>io.netty</groupId>
                <artifactId>netty-transport-native-kqueue</artifactId>
                <version>${netty.version}</version>
                <classifier>${os.detected.classifier}</classifier>
                <scope>runtime</scope>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

See [examples/weather/pom.xml](../examples/weather/pom.xml) for a complete working setup.

Requesting an explicit engine whose transport is not on the classpath (or not supported by the OS) throws `UnsupportedOperationException` at startup, with Netty's unavailability cause in the message:

```java
network(n -> n.ioEngine(NettyIoEngine.EPOLL)) // fails fast on macOS
```

```kotlin
network { ioEngine = NettyIoEngine.EPOLL }
```

## Session

Configured via `session { }` / `SessionConfig.Builder`. Sessions are off by default
(stateless server). All other session options require `enabled = true` — configuring
them on a stateless server fails at construction.

| Option | Default | Description |
|---|---|---|
| `enabled` | `false` | Server-side sessions are off by default (stateless). Set `true` to create sessions with TTL tracking |
| `sessionTtl` | `30s` | Idle sessions are evicted after this duration |
| `janitorInterval` | `5s` | Janitor sweep interval; controls how often expired sessions are checked |
| `sessionEventStore` | in-memory | Custom session event store |
| `sessionStore` | in-memory | Custom session store |
| `sessionIdGenerator` | `sess_<uuid>` | Custom hook for deriving session ids from the initialize `HttpRequest` (headers/URI) |

## Runtime

Configured via `runtime { }` / `RuntimeConfig.Builder`.

| Option | Default | Description |
|---|---|---|
| `shutdownGracePeriod` | `5s` | Time in-flight handlers get to drain on `close()`; `ZERO` interrupts immediately |
| `requestTimeout` | `60s` | Timeout for pending requests sent to the client |

## Monitoring

Configured via `monitoring { }` / `MonitoringConfig.Builder`. Off by default.

| Option | Default | Description |
|---|---|---|
| `slowRequestLogging` | `false` | Enable slow-request diagnostics (handler watchdog + slow-POST logging) |
| `slowRequestThreshold` | `10s` | Requests exceeding this duration produce a `warn` log when logging is enabled |

Turning `slowRequestLogging` on activates two diagnostics:
- **Handler watchdog** — a scheduled timer logs `"Handler slow"` at `debug` level when a handler exceeds the threshold
- **Slow-POST logging** — `completePostRequest` logs `"Slow POST response"` at `warn` level when the full request cycle exceeds the threshold

Both share the same threshold and are silenced at default (flag off, zero overhead).

```java
TachyonServer.builder()
    .monitoring(m -> m.slowRequestLogging().slowRequestThreshold(Duration.ofSeconds(5)))
    .port(8080)
    .start();
```

```kotlin
TachyonServer(port = 8080) {
    monitoring {
        slowRequestLogging = true
        slowRequestThreshold = 5.seconds
    }
}
```

## Identity

`info { }` sets the `serverInfo` returned by `initialize` (name, version, title, etc.); `.name(String)` is a top-level shortcut. See [quickstart](quickstart.md) for a full example.

## Capabilities

Configured via `capabilities { }` / `CapabilitiesConfig.Builder`. Each MCP capability has its own
config type, nested under `capabilities`: `tools` and `prompts` share `FeatureConfig`
(`mode`, `listChanged`, `pageSize`); `resources` uses `ResourcesConfig` (adds `subscribe`); `tasks`
uses `TasksConfig`, whose fields map 1:1 to the MCP `tasks` capability object (`tasks.list`,
`tasks.cancel`, `tasks.requests.tools.call`).

| Sub-config | Fields | Default |
|---|---|---|
| `tools` / `prompts` (`FeatureConfig`) | `mode`, `listChanged`, `pageSize` | `AUTO`, `false`, `50` |
| `resources` (`ResourcesConfig`) | `mode`, `listChanged`, `pageSize`, `subscribe` | `AUTO`, `false`, `50`, `false` |
| `tasks` (`TasksConfig`) | `enabled`, `list`, `cancel`, `requests`, `pageSize`, `keepAlive`, `pollInterval` | `false` × 4, `50`, `5m`, none |
| — | `completions`, `logging` | `AUTO`, `false` |

`mode`:
- **`AUTO`** (default) — advertised only once a handler of that type is registered. No config needed for the common case.
- **`ON`** — advertised from `initialize`, even with zero handlers registered yet (needed for dynamic registration + `list_changed` after startup).
- **`OFF`** — never advertised, **and registration becomes a no-op**: `tools().register(...)`, `resources().register(...)`, and `prompts().register(...)` are silently skipped (logged at `debug`).

`tasks.enabled` works the same as `mode == ON` for tools/resources/prompts, except the `tasks`
capability is *also* advertised — regardless of `enabled` — whenever a registered tool declares
task augmentation support (`ToolDescriptor.taskSupport()`).

```java
TachyonServer.builder()
    .capabilities(c -> c
        .tools(FeatureConfig.builder().mode(Mode.ON).listChanged(true).build())
        .resources(ResourcesConfig.builder().mode(Mode.ON).subscribe(true).build())
        .tasks(TasksConfig.builder().enabled(true).list(true).build())
        .completions()
        .logging())
    .port(8080)
    .start();
```

```kotlin
TachyonServer(port = 8080) {
    capabilities {
        tools { mode = Mode.ON; listChanged = true }
        resources { mode = Mode.ON; subscribe = true }
        tasks { enabled = true; list = true }
        completions = true
        logging = true
    }
}
```

Java also keeps the pre-nesting flat setters and convenience shortcuts (`.tools()`,
`.tools(listChanged)`, `.noTools()`, `.toolsPageSize(n)`, and the `resources`/`prompts`/`tasks`
equivalents) — they mutate the same nested sub-config, so chaining still works:
`c.tools().toolsPageSize(20)` sets `mode = ON` and `pageSize = 20` on the same `FeatureConfig`.

## Advanced

| Option | Description |
|---|---|
| `executor(ExecutorService)` | Handler executor; default runs handlers on virtual threads |
| `threadFactory(ThreadFactory)` | Thread factory for the default executor |
| `pipelineCustomizer(Consumer<ChannelPipeline>)` | Hook to mutate the Netty pipeline after MCP handlers are installed |
| `json(cfg -> cfg.inputSchemaValidator(...).outputSchemaValidator(...))` | Custom JSON Schema validators |

## Examples

Full, runnable servers and configuration references live in the Tachyon skill resources:

- Java: `.agents/skills/tachyon-mcp/resources/java/`
  - [`ServerBasic.java`](../.agents/skills/tachyon-mcp/resources/java/ServerBasic.java) — complete server with tool, resource, template, and prompt
  - [`ToolHandlerExample.java`](../.agents/skills/tachyon-mcp/resources/java/ToolHandlerExample.java) — `ToolHandler.of()`, `extends AbstractToolHandler`, long-running keep-alive
  - [`ResourceHandlerExample.java`](../.agents/skills/tachyon-mcp/resources/java/ResourceHandlerExample.java) — static resources and URI templates
  - [`PromptHandlerExample.java`](../.agents/skills/tachyon-mcp/resources/java/PromptHandlerExample.java) — simple and handler-based prompts
  - [`ConfigReference.java`](../.agents/skills/tachyon-mcp/resources/java/ConfigReference.java) — all config builder patterns in one file
- Kotlin: `.agents/skills/tachyon-mcp/resources/kotlin/`
  - [`ServerBasic.kt`](../.agents/skills/tachyon-mcp/resources/kotlin/ServerBasic.kt) — full server via Kotlin DSL
  - [`ToolHandlerExample.kt`](../.agents/skills/tachyon-mcp/resources/kotlin/ToolHandlerExample.kt) — suspend handler, `registerTool`
  - [`ResourceHandlerExample.kt`](../.agents/skills/tachyon-mcp/resources/kotlin/ResourceHandlerExample.kt) — resources and templates in DSL
  - [`PromptHandlerExample.kt`](../.agents/skills/tachyon-mcp/resources/kotlin/PromptHandlerExample.kt) — prompts in DSL
