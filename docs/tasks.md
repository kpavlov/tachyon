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
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tasks.TaskState;

McpServer server = handle.server();

// Create
TaskEntry task = server.tasks().create("task-001", "Processing...", null);

// Update state
server.tasks().update(task.id(), TaskState.WORKING, "Running step 1...");

// Complete
server.tasks().update(task.id(), TaskState.COMPLETED, "Done");
```

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

Tachyon runs a background janitor that removes stale tasks past their TTL. Configure TTL per task or rely on the server default (30 s session TTL applies).

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
