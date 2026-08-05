# Tasks — Tachyon MCP Server

Tasks track long-running operations across multiple MCP exchanges. Tachyon implements the MCP task state machine and broadcasts status notifications to clients.

## Enable tasks

Tasks are enabled automatically when you configure task capabilities:

```java
var server = TachyonServer.builder()
    .capabilities(cfg -> cfg
        .tasks(true, true, true))  // list=true, cancel=true, inputRequests=true
    .port(8080)
    .build();
server.start();
```

## Task state machine

```
SUBMITTED → WORKING → COMPLETED
                    → FAILED
                    → CANCELLED
          → INPUT_REQUIRED → WORKING (on response)
          → REJECTED
          → AUTH_REQUIRED
```

Tachyon enforces valid transitions. Invalid moves throw `IllegalStateException`.

## Create and update tasks

```java
import dev.tachyonmcp.api.server.domain.Task;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.features.tasks.TaskOptions;

import java.util.List;

TachyonServer server = TachyonServer.builder().port(8080).build();
server.

start();

// Create — server generates the ID
Task task = server.tasks().create();

// Create — correlate with your own task runner's ID
Task ownedTask = server.tasks().create(
        TaskOptions.builder().id("my-runner-task-42").build());

// Update state via the returned Task handle
ownedTask.

updateMessage("Running step 1...");
ownedTask.

complete(TaskResult.completed(List.of(TextContent.of("done")),null,null));
```

Supply `TaskOptions.builder().id(...)` to map a task onto an ID from your own external task
runner. IDs must be unique — `create` throws `IllegalArgumentException` if a task with that ID
already exists. Leave `id` unset to let the server generate one (`UUID.randomUUID()`-backed,
same idiom as session IDs).

Supply `TaskOptions.builder().keepAlive(Duration.ofMinutes(30)).build()` to override how long
this task's result stays retrievable via `tasks/get`/`tasks/result` after it reaches a terminal
state, overriding the server-wide default (`TasksConfig.keepAlive`, 5 minutes).

Status notifications are broadcast automatically on each transition.

## MCP 2025-11-25 task-augmented tool calls

Declare `taskSupport` on a tool descriptor and clients can run the tool as a background task
by adding a `task` field to `tools/call`:

```java
import dev.tachyonmcp.api.server.features.tasks.TaskSupport;

var descriptor = ToolDescriptor.builder()
        .name("import-data")
        .description("Long-running import")
        .taskSupport(TaskSupport.OPTIONAL)  // or REQUIRED; default FORBIDDEN
        .build();
```

```json
{"method": "tools/call", "params": {"name": "import-data", "arguments": {}, "task": {"ttl": 60000}}}
```

The server responds with a `CreateTaskResult` immediately and executes the handler in the
background — sync, async, and Kotlin suspend handlers all work. The client then polls
`tasks/get` and fetches the outcome via `tasks/result`.

- `taskSupport = REQUIRED` rejects plain calls; `FORBIDDEN` (default) rejects task-augmented calls.
- `task.ttl` (milliseconds) bounds retention; expired tasks transition to `FAILED`.
- `tasks/cancel` interrupts synchronous handlers running on virtual threads and cancels the
  `CompletionStage` returned by asynchronous handlers. Kotlin suspend handlers receive coroutine
  cancellation.

## MCP 2026-07-28 tasks (SEP-2663)

MCP 2026-07-28 has no session, so task creation moved from a client-requested opt-in to a
**server-directed** extension: the legacy `task` field on `tools/call` is ignored outright (any
value, well-formed or not), and clients instead declare readiness to receive a task per request via
`_meta."io.modelcontextprotocol/clientCapabilities".extensions."io.modelcontextprotocol/tasks"`.

- A `taskSupport = REQUIRED` tool always creates a task under 2026-07-28 (it can't run
  synchronously) — the server returns `CreateTaskResult` immediately if the caller declared the
  extension, or a `-32021` (Missing Required Client Capability) error if it didn't.
  `OPTIONAL`/`FORBIDDEN` tools run synchronously, same as always.
- `tasks/get` and `tasks/cancel` remain, gated by the same per-request declaration (`-32021` if
  undeclared). `tasks/list` and `tasks/result` are removed outright (`-32601`) — `tasks/list` has
  no caller-scoping mechanism under a sessionless protocol, and `tasks/result` is replaced by the
  outcome inlined directly into `tasks/get`'s `result`/`error` fields once the task reaches a
  terminal state.
- Wire shape differs from 2025-11-25's experimental fields: `ttlMs`/`pollIntervalMs` (not
  `ttl`/`pollInterval`), a `resultType` discriminator (`"task"` on task creation, `"complete"` on
  `tasks/get`), a flat `CreateTaskResult` (no `{"task": {...}}` wrapper), and `tasks/cancel`
  returning a bare `{"resultType": "complete"}` acknowledgement rather than the task's full state.
- A tool result that completes with `isError: true` reports task status `completed` (with the
  error content inlined into `result`), not `failed` — `failed` is reserved for genuine JSON-RPC
  protocol errors.

Not yet implemented: `tasks/update` (SEP-2663's replacement for submitting `inputResponses` to a
task in `input_required` state), inlining `inputRequests` into `tasks/get` for that same state, and
server-to-client `notifications/tasks` push (needs `subscriptions/listen` over the stateless
2026-07-28 transport, which doesn't exist yet).

## TasksExtension

`TasksExtension` (`io.modelcontextprotocol/tasks`) serves double duty. Under 2025-11-25 it's a
negotiable extension example: it exposes a `create_task` tool and a `task://{id}` resource
template only to clients that opt in during `initialize`. Under 2026-07-28, its ID is exactly the
SEP-2663 capability gate described above — registering it (`.extension(TasksExtension.instance())`)
is what makes `context.isExtensionEnabled(TasksExtension.ID)` satisfiable for a client that
declares it per request.

```java
import dev.tachyonmcp.core.server.features.tasks.TasksExtension;

var server = TachyonServer.builder()
    .extension(TasksExtension.instance())
    .port(8080)
    .build();
server.start();
```

MCP 2025-11-25 clients that include `"extensions": {"io.modelcontextprotocol/tasks": {}}` in their `initialize` capabilities receive the extension tool and resource. Clients that don't negotiate see standard `tasks/*` methods only.

## Task janitor

A background janitor sweeps every 30s and does two independent things:

- **Active tasks** (`WORKING`/`INPUT_REQUIRED`) past their `task.ttl` are transitioned to `FAILED`.
- **Terminal tasks'** (`COMPLETED`/`FAILED`/`CANCELLED`) results are dropped from memory once
  `keepAlive` has elapsed since they entered the terminal state — default 5 minutes, configurable
  server-wide via `TasksConfig.keepAlive`/`CapabilitiesConfig.Builder.tasksKeepAlive(...)`
  (Kotlin DSL: `tasks { keepAlive = 10.minutes }`), or per task via `TaskOptions.keepAlive(...)`.
  Once dropped, `tasks/get` (2025-11-25 and 2026-07-28) and `tasks/result` (2025-11-25 only —
  removed outright under 2026-07-28, see below) return "Task not found", same as an ID that never
  existed.

## MCP methods

| Method | Description | 2026-07-28 |
|---|---|---|
| `tasks/list` | List tasks, paginated | removed (`-32601`) |
| `tasks/get` | Get a task by ID | kept, gated by the tasks extension |
| `tasks/cancel` | Cancel a running task | kept, gated by the tasks extension |
| `tasks/result` | Get the task payload result | removed (`-32601`) unconditionally, even for a valid, unexpired task ID — outcome inlined into `tasks/get` instead |

Notifications: `notifications/tasks/list_changed`, `notifications/tasks/status` — 2025-11-25 only.
Not implemented for 2026-07-28 (no session to push to; see "Not yet implemented" above).

---

**See also:** [Extensions](extensions.md) · [Tools](tools.md) · [Quickstart](quickstart.md)
