# Tasks

Tachyon exposes external work as MCP tasks. The application, workflow engine, or job system owns
execution. Tachyon owns protocol mapping, a small snapshot cache, and notifications.

## Configure an execution connector

Implement `TaskExecutionEngine` for the system that owns the work:

```java
public final class WorkflowTasks implements TaskExecutionEngine {
    @Override
    public Set<TaskFeature> supportedFeatures() {
        return Set.of(TaskFeature.CANCEL, TaskFeature.REQUESTS);
    }

    @Override
    public TaskSnapshot refresh(InteractionContext context, String taskId) {
        return workflows.snapshot(taskId);
    }

    @Override
    public TaskSnapshot cancel(InteractionContext context, String taskId) {
        workflows.cancel(taskId);
        return workflows.snapshot(taskId);
    }

    @Override
    public void submitInput(InteractionContext context, String taskId, TaskInput input) {
        workflows.submitInput(taskId, input);
    }
}
```

Enable only operations implemented by the connector:

```java
var taskEngine = new WorkflowTasks();

var server = TachyonServer.builder()
        .capabilities(c -> c.tasks(taskEngine, false, true, true))
        .port(8080)
        .build();
```

Tasks are off by default. There is no built-in in-process engine. Enabling tasks without an engine
fails during server configuration. `tasks/get` is part of the base connector contract. Optional
features are:

| Feature | Connector hook | MCP method |
|---|---|---|
| `CANCEL` | `cancel` | `tasks/cancel` |
| `REQUESTS` | `submitInput` | `tasks/update` |
| `LIST` | `LegacyTaskExecutionEngine.list` | legacy `tasks/list` |

Legacy protocol support for `tasks/list` and blocking `tasks/result` requires
`LegacyTaskExecutionEngine`, which extends `TaskExecutionEngine` and adds `list` and `awaitResult`.

## Return a task from a tool

Mark the tool as task-capable. Its handler starts external work once and returns the initial
immutable projection:

```java
server.tools().register(
        tool -> tool.name("book_appointment").taskSupport(TaskSupport.REQUIRED),
        (context, request) -> {
            var workflowId = workflows.start(request.arguments());
            return ToolResult.task(
                    TaskSnapshot.working(workflowId, clock.instant(), 1));
        });
```

Use the external system's stable, safe identifier as `taskId`. For Temporal, use Workflow ID rather
than Run ID so Continue-As-New keeps one logical MCP task.

The flow is:

1. The handler starts external work.
2. It returns `ToolResult.task(initialSnapshot)`.
3. Tachyon publishes that snapshot and maps the task response.
4. `tasks/get` calls `refresh` and publishes the authoritative returned snapshot.
5. `tasks/update` forwards `TaskInput` to `submitInput`.
6. `tasks/cancel` calls `cancel` and publishes the returned cancelled snapshot.

Tachyon never runs the handler in the background and never invokes it again for `tasks/update`.

## Publish snapshots

Push updates through the public `Tasks` façade when the external system sends a callback:

```java
server.tasks().publish(TaskSnapshot.builder()
        .taskId(workflowId)
        .status(TaskState.WORKING)
        .statusMessage("Charging card")
        .createdAt(createdAt)
        .lastUpdatedAt(clock.instant())
        .revision(4)
        .build());
```

`Tasks` contains only projection operations, plus one ephemeral notification:

```java
TaskSnapshot publish(TaskSnapshot snapshot);
@Nullable TaskSnapshot get(String taskId);
boolean remove(String taskId);
void reportProgress(String taskId, double progress, @Nullable Double total, @Nullable String message);
```

Each snapshot carries a monotonically increasing `revision`. Tachyon ignores duplicate or older
revisions. Push improves notification latency, but pull remains authoritative: `tasks/get` always
calls the connector.

Terminal snapshots may carry `TaskResult`:

```java
var completed = TaskSnapshot.builder()
        .from(previous)
        .status(TaskState.COMPLETED)
        .result(TaskResult.completed(Map.of("bookingId", bookingId)))
        .lastUpdatedAt(clock.instant())
        .revision(previous.revision() + 1)
        .build();
```

The cache retention window removes expired terminal projections only. It never cancels or mutates
external work.

## Report progress

`reportProgress` emits `notifications/progress` addressed to the progress token of the request that
created the task — it is not part of `TaskSnapshot` and carries no revision:

```java
server.tasks().reportProgress(workflowId, 40.0, 100.0, "Charging card");
```

Only a task created by a task-augmented tool call has a progress token to notify — a task known to
Tachyon solely through `publish(TaskSnapshot)` has none, so `reportProgress` for it is a no-op,
logged at debug.

## Kotlin

Kotlin uses the same Java connector:

```kotlin
capabilities {
    tasks {
        enabled = true
        list = false
        cancel = true
        requests = true
        executionEngine = taskEngine
    }
}
```

Tool handlers return the same `ToolResult.task(TaskSnapshot)` branch.

## Temporal

Use `tachyon-tasks-temporal` when Temporal owns execution. The adapter exposes a concrete `start`
helper because Temporal has a known start contract; that helper is deliberately not part of the
generic `TaskExecutionEngine` SPI. See [the Temporal example](../examples/temporal/README.md).
