# Tasks — Tachyon MCP Server

Tasks track long-running operations across multiple MCP exchanges. Tachyon implements the MCP task state machine and broadcasts status notifications to clients.

## Enable tasks

Tasks are enabled automatically when you configure task capabilities:

```java
TachyonServer.builder()
    .capabilities(cfg -> cfg
        .tasks(true, true, true))  // list=true, cancel=true, inputRequests=true
    .port(8080)
    .start();
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
import dev.tachyonmcp.server.domain.Task;
import dev.tachyonmcp.server.domain.TaskResult;
import dev.tachyonmcp.server.features.tasks.TaskOptions;

Server server = handle.server();

// Create — server generates the ID
Task task = server.tasks().create();

// Create — correlate with your own task runner's ID
Task ownedTask = server.tasks().create(
        TaskOptions.builder().id("my-runner-task-42").build());

// Update state via the returned Task handle
ownedTask.updateMessage("Running step 1...");
ownedTask.complete(new TaskResult.Completed(...));
```

Supply `TaskOptions.builder().id(...)` to map a task onto an ID from your own external task
runner. IDs must be unique — `create` throws `IllegalArgumentException` if a task with that ID
already exists. Leave `id` unset to let the server generate one (`UUID.randomUUID()`-backed,
same idiom as session IDs).

Supply `TaskOptions.builder().keepAlive(Duration.ofMinutes(30)).build()` to override how long
this task's result stays retrievable via `tasks/get`/`tasks/result` after it reaches a terminal
state, overriding the server-wide default (`TasksConfig.keepAlive`, 5 minutes).

Status notifications are broadcast automatically on each transition.

## Task-augmented tool calls

Declare `taskSupport` on a tool descriptor and clients can run the tool as a background task
by adding a `task` field to `tools/call`:

```java
import dev.tachyonmcp.server.features.tasks.TaskSupport;

var descriptor = ToolDescriptor.builder("import-data")
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
- `tasks/cancel` interrupts the executing virtual thread — blocking handlers see
  `InterruptedException`, Kotlin suspend handlers get coroutine cancellation.

## TasksExtension (SEP-1686)

`TasksExtension` is a negotiable protocol extension. It exposes a `create_task` tool and a `task://{id}` resource template only to clients that opt in during `initialize`.

```java
import dev.tachyonmcp.server.features.tasks.TasksExtension;

TachyonServer.builder()
    .extension(TasksExtension.instance())
    .port(8080)
    .start();
```

Clients that include `"extensions": {"io.modelcontextprotocol/tasks": {}}` in their `initialize` capabilities receive the extension tool and resource. Clients that don't negotiate see standard `tasks/*` methods only.

## Task janitor

A background janitor sweeps every 30s and does two independent things:

- **Active tasks** (`WORKING`/`INPUT_REQUIRED`) past their `task.ttl` are transitioned to `FAILED`.
- **Terminal tasks'** (`COMPLETED`/`FAILED`/`CANCELLED`) results are dropped from memory once
  `keepAlive` has elapsed since they entered the terminal state — default 5 minutes, configurable
  server-wide via `TasksConfig.keepAlive`/`CapabilitiesConfig.Builder.tasksKeepAlive(...)`
  (Kotlin DSL: `tasks { keepAlive = 10.minutes }`), or per task via `TaskOptions.keepAlive(...)`.
  Once dropped, `tasks/get`/`tasks/result` return "Task not found", same as an ID that never
  existed.

## MCP methods

| Method | Description |
|---|---|
| `tasks/list` | List tasks, paginated |
| `tasks/get` | Get a task by ID |
| `tasks/cancel` | Cancel a running task |
| `tasks/result` | Get the task payload result |

Notifications: `notifications/tasks/list_changed`, `notifications/tasks/status`

---

**See also:** [Extensions](extensions.md) · [Tools](tools.md) · [Quickstart](quickstart.md)
