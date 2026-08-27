# MCP Tasks with Temporal

This example keeps the ownership boundary sharp: one MCP task maps to one [Temporal](https://temporal.io) Workflow ID.
The `tachyon-tasks-temporal` integration starts, refreshes, updates, and cancels through
`TemporalTaskExecutionEngine`. Temporal owns durability, retries, timers, and business execution.

```java
var taskExecutionEngine = BookingTaskEngine.create(workflowClient, "bookings");

var server = TachyonServer.builder()
        .capabilities(c -> c.tasks(taskExecutionEngine, false, true, true))
        .port(8080)
        .build();

server.tools().register(
        tool -> tool.name("book_appointment").taskSupport(TaskSupport.REQUIRED),
        (context, request) -> ToolResult.task(taskExecutionEngine.start(
                context,
                TaskExecutionRequest.builder()
                        .taskId(UUID.randomUUID().toString())
                        .operation("book_appointment")
                        .arguments(request.arguments())
                        .meta(request.meta())
                        .build())));
```

The adapter uses the Tachyon task ID as the Temporal Workflow ID. It deliberately does not retain a
Temporal Run ID, so Continue-As-New preserves the logical MCP task identity.

## Test locally

Install the Tachyon snapshots, then run the JUnit 5 in-memory and Testcontainers tests:

```shell
./mvnw install -pl tachyon-core,integrations/tachyon-tasks-temporal -am -DskipTests
./mvnw test -f examples/temporal/pom.xml
```

Docker is required for the container test. When Docker is unavailable, the JUnit 5 Testcontainers
extension skips that test while the `TestWorkflowEnvironment` test still runs.
