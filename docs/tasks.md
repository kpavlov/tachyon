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
SUBMITTED → WORKING → INPUT_REQUIRED → WORKING (once the client answers)
          ↘        ↘                ↘
            COMPLETED / FAILED / CANCELLED   (terminal, reachable from all three)
```

`INPUT_REQUIRED` is reachable only from `WORKING` — `requireInput` on a `SUBMITTED` task is
rejected, so `start()` first. Terminal states accept nothing further. (`REJECTED`, `AUTH_REQUIRED`
and `UNKNOWN` exist in the enum for A2A parity but have no public API to reach them.)

Tachyon enforces valid transitions, and **rejections are returned, not thrown**: every mutator
(`start`, `updateMessage`, `requireInput`, `complete`, `fail`, `cancel`) returns `boolean` — `false`
means the task was in a state that does not allow it, and nothing changed. Check the return value if
the transition matters. Only programmer error throws: a `null` argument, or `complete(null)`.

### What each state accepts

| State | Accepts |
|---|---|
| `SUBMITTED` | `start`, `complete`, `fail`, `cancel` |
| `WORKING` | `updateMessage`, `requireInput`, `complete`, `fail`, `cancel` |
| `INPUT_REQUIRED` | `updateMessage`, `complete`, `fail`, `cancel` |
| `COMPLETED` / `FAILED` / `CANCELLED` | nothing |

Anything not listed returns `false` and leaves the task untouched. This table is enforced by
`TasksWorkflowTest`, which drives a real task into every state and tries every operation.

## Create and update tasks

```java
import dev.tachyonmcp.api.server.domain.Task;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.features.tasks.TaskOptions;

import java.util.List;

TachyonServer server = TachyonServer.builder().port(8080).build();
server.start();

// Create — server generates the ID
Task task = server.tasks().create();

// Create — correlate with your own task runner's ID
Task ownedTask = server.tasks().create(
        TaskOptions.builder().id("my-runner-task-42").build());

// Update state via the returned Task handle
ownedTask.start("Starting...");
ownedTask.updateMessage("Running step 1...");
ownedTask.complete(TaskResult.completed(List.of(TextContent.of("done")), null, null));
```

If your task will pause for input, create it with a resumer — otherwise the answers the client
submits have nowhere to go and are dropped:

```java
import dev.tachyonmcp.api.server.domain.FormInputRequest;
import dev.tachyonmcp.api.server.domain.InputRequestBundle;

Task signup = server.tasks().create(
        TaskOptions.builder().id("signup-42").build(),
        (paused, inputResponses, requestState) ->
                paused.complete(TaskResult.completed(register(inputResponses))));

signup.start();
signup.requireInput(
        new InputRequestBundle(Map.of("email", FormInputRequest.of("Your email?", emailSchema)), "round-1"),
        "waiting for an email address");
// ... client answers via tasks/update -> the resumer above runs and completes the task
```

The resumer is called once per round, after every outstanding key has an answer, with the task
already back in `WORKING`. Drive it onwards from there: complete it, fail it, or call
`requireInput` again for another round. Throwing from the resumer fails the task.

Task-augmented tool calls need no resumer: the server re-invokes the tool handler instead, passing
the answers as `ToolRequest.inputResponses()` and echoing `requestState()`.

A created task is `SUBMITTED` until `start(...)` moves it to `WORKING` — see
[what each state accepts](#what-each-state-accepts). An `INPUT_REQUIRED` task is resumed by the
client via [`tasks/update`](#tasksupdate--submitting-input-to-a-paused-task), never by the server.

Supply `TaskOptions.builder().id(...)` to map a task onto an ID from your own external task
runner. IDs must be unique — `create` throws `IllegalArgumentException` if a task with that ID
already exists. Leave `id` unset to let the server generate one (`UUID.randomUUID()`-backed,
same idiom as session IDs).

Supply `TaskOptions.builder().keepAlive(Duration.ofMinutes(30)).build()` to override how long
this task's result stays retrievable via `tasks/get`/`tasks/result` after it reaches a terminal
state, overriding the server-wide default (`TasksConfig.keepAlive`, 5 minutes).

`Task.reportProgress(progress, total, message)` emits `notifications/progress` addressed to the
progress token of the request that created the task. Tasks created without one — including every
`tasks().create(...)` task, since `TaskOptions` carries no token — have nobody to notify, so the call
is a no-op logged at debug. `total` and `message` are optional and omitted from the wire when null.

Status notifications are broadcast automatically on each transition.
`Task.createdAt()` and `Task.lastUpdatedAt()` expose the creation and most recent status/message
update timestamps as `Instant` values.

## Task outcomes

Every task ends in `COMPLETED`, `FAILED`, or `CANCELLED`. From a tool handler you choose by what
you return; from your own code, by which method you call.

| Outcome | From a tool handler | From your own code | Wire status |
|---|---|---|---|
| Success | `return ToolResult.structured(payload)` | `task.complete(TaskResult.completed(payload))` | `completed` |
| Business error | `return ToolResult.error("no such city")` | `task.fail(TaskResult.failed("no such city"))` | `completed` under 2026-07-28 (error inlined), `failed` under 2025-11-25 |
| Protocol failure | throw, or `TaskResult.failed(ServerError)` | `task.fail(TaskResult.failed(serverError))` | `failed` |
| Cancelled | — (client-driven) | `task.cancel("user withdrew it")` | `cancelled` |

⚠️ The business-error/protocol-failure split is deliberate: under 2026-07-28 a tool that *ran fine
and reported a negative result* is `completed`, and `failed` is reserved for genuine JSON-RPC errors.

## Awaiting a task in-process

`Task.completion()` settles when the task reaches a terminal state. Use `whenComplete` rather than
`join()` — cancellation arrives as a `CancellationException`, and `join()` replaces its message:

```java
task.completion().whenComplete((result, failure) -> {
    if (failure instanceof CancellationException) {
        log.info("cancelled: {}", task.statusMessage());   // the reason lives here
    } else if (failure != null) {
        log.error("failed", failure);
    } else {
        log.info("completed: {}", result);
    }
});
```

`toCompletableFuture().isCancelled()` is `true` for a cancelled task. The reason passed to
`cancel(reason)` reaches `whenComplete`/`handle` but **not** `join()`/`get()`, which throw a fresh
`CancellationException` of their own — read it from `statusMessage()` instead.

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

⚠️ `OPTIONAL` only means "optional" under 2025-11-25, where the client opts in per call. Under
2026-07-28 there is no client-side opt-in, so `OPTIONAL` and `FORBIDDEN` both run **synchronously**
and only `REQUIRED` ever creates a task. Pick `REQUIRED` for anything that must run in the
background on both protocol versions.

Kotlin DSL — the name-based `tool(...)` registration takes `taskSupport` directly, no descriptor needed:

```kotlin
tool("import-data", description = "Long-running import", taskSupport = TaskSupport.OPTIONAL) {
    success(runImport())
}
```

```json
{"method": "tools/call", "params": {"name": "import-data", "arguments": {}, "task": {"ttl": 60000}}}
```

The server responds with a `CreateTaskResult` immediately and executes the handler in the
background — sync, async, and Kotlin suspend handlers all work. The client then polls
`tasks/get` and fetches the outcome via `tasks/result`.

⚠️ In a task-augmented tool the **returned `ToolResult` is the sole authority** on the outcome — the
server applies it to the task for you. Do not also call `Task.complete`/`fail`/`cancel` from the
handler: whichever runs first wins, the other is silently discarded, and the server logs a warning
when the returned result is the one that loses. Use `request.task()` for progress and
`requireInput`, not for finishing.

- `taskSupport = REQUIRED` rejects plain calls; `FORBIDDEN` (default) rejects task-augmented calls.
- `task.ttl` (milliseconds) bounds retention; expired tasks transition to `FAILED`. Zero or negative
  `ttl` means "never expires" — same convention as `keepAlive` below.
- `tasks/cancel` interrupts synchronous handlers running on virtual threads and cancels the
  `CompletionStage` returned by asynchronous handlers. Kotlin suspend handlers receive coroutine
  cancellation. `Task.completion()` is cancelled too (`isCancelled()`, not a plain failure), and
  `tasks/result` answers `-32602 "Task was cancelled"` — whether the cancellation lands before the
  call or while the caller is still blocked on it.

## Pausing a tool for input

Return `ToolResult.inputRequired(...)` and the task parks in `input_required`. When the client
answers via [`tasks/update`](#tasksupdate--submitting-input-to-a-paused-task), the server re-invokes
**the same handler** with the answers and the `requestState` you set:

```java
server.tools().register(descriptor, (ctx, request) -> {
    var answers = request.inputResponses();
    if (answers == null || !answers.containsKey("city")) {
        return ToolResult.inputRequired(
                Map.of("city", FormInputRequest.of("Which city?", citySchema)), null);
    }
    return ToolResult.structured(forecastFor(answers.get("city")));
});
```

```kotlin
tool("forecast", taskSupport = TaskSupport.REQUIRED) {
    val city = request.inputResponses()?.get("city")
        ?: return@tool inputRequired("city" to FormInputRequest("Which city?", citySchema))
    success(forecastFor(city))
}
```

The handler restarts from scratch each round, so nothing on the stack survives — `requestState` is
your only continuity, and each round's answers are per-round (round 2 never sees round 1's map).
Carry earlier answers forward in `requestState` if you need them:

```java
var state = request.requestState();
if (state == null) {
    return ToolResult.inputRequired(Map.of("name", FormInputRequest.of("Name?", s)), "asked-name");
}
if ("asked-name".equals(state)) {
    return ToolResult.inputRequired(Map.of("email", FormInputRequest.of("Email?", s)), "asked-email");
}
return ToolResult.structured(register(request.inputResponses()));
```

⚠️ Pausing works on both protocol versions, but **resuming is 2026-07-28 only** — 2025-11-25 has no
wire field to answer by `taskId`, so a task that parks there can never be resumed. Under that
version, treat `inputRequired` from a task-augmented tool as a dead end.

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

### `tasks/update` — submitting input to a paused task

When a task-augmented tool moves to `input_required`, `tasks/get` inlines the outstanding
`inputRequests`. The client answers them with `tasks/update`, keyed to the same `taskId`:

```json
{"jsonrpc":"2.0","id":1,"method":"tasks/update","params":{
  "taskId":"tid_...","inputResponses":{"user_name":{"name":"Alice"}}}}
```

The server always acknowledges immediately with an empty result (`{"resultType":"complete"}`) —
resumption happens asynchronously afterward, not as part of the `tasks/update` response. The task
resumes (re-invoking the same handler, `requestState` intact) only once every currently-outstanding
`inputRequests` key has a response; a client may spread the answers across multiple `tasks/update`
calls for a multi-key bundle, and the task stays `input_required` until the last one arrives.
Unknown or already-satisfied keys in a submission are silently ignored, not rejected.

`tasks/update` and `inputRequests`-on-`tasks/get` are **2026-07-28 only**. MCP 2025-11-25's task
model predates SEP-2663 and has no wire field for either — an `input_required` task under that
protocol version has no way to resume by `taskId` at all; a client on that version can only retry
the original request with `inputResponses`/`requestState`, which creates an unrelated new task
rather than resuming the original (see `TaskAugmentedToolTest` for this documented limitation).

Not yet implemented: server-to-client `notifications/tasks` push (needs `subscriptions/listen` over
the stateless 2026-07-28 transport, which doesn't exist yet).

## TasksExtension

`TasksExtension` (`io.modelcontextprotocol/tasks`) serves double duty. Under 2025-11-25 it's a
negotiable extension example: it exposes a `create_task` tool and a `task://{id}` resource
template only to clients that opt in during `initialize`. Under 2026-07-28, its ID is exactly the
SEP-2663 capability gate described above — registering it (`.withExtensions(TasksExtension.instance())`)
is what makes `context.isExtensionEnabled(TasksExtension.ID)` satisfiable for a client that
declares it per request.

```java
import dev.tachyonmcp.core.server.features.tasks.TasksExtension;

final var server = TachyonServer.builder()
    .withExtensions(TasksExtension.instance())
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
| `tasks/get` | Get a task by ID | kept, gated by the tasks extension; inlines `inputRequests` while `input_required` |
| `tasks/cancel` | Cancel a running task | kept, gated by the tasks extension |
| `tasks/result` | Get the task payload result | removed (`-32601`) unconditionally, even for a valid, unexpired task ID — outcome inlined into `tasks/get` instead |
| `tasks/update` | Submit `inputResponses` to a paused task | not available under 2025-11-25 (`-32601` — predates SEP-2663's wire shape); gated by the tasks extension under 2026-07-28, same as `tasks/get`/`tasks/cancel` |

Notifications: `notifications/tasks/list_changed`, `notifications/tasks/status` — 2025-11-25 only.
Not implemented for 2026-07-28 (no session to push to; see "Not yet implemented" above).

---

**See also:** [Extensions](extensions.md) · [Tools](tools.md) · [Quickstart](quickstart.md)
