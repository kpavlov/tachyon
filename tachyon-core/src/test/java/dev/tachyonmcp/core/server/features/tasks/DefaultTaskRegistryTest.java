/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import static dev.tachyonmcp.core.test.TestUtils.decodeAndHandle;
import static dev.tachyonmcp.core.test.TestUtils.newEngine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.server.domain.FormInputRequest;
import dev.tachyonmcp.api.server.domain.InputRequestBundle;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.tasks.TaskOptions;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.protocol.Protocols;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.CallToolResult;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.CancelTaskResult;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.GetTaskResult;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.ListTasksResult;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.config.TasksConfig;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.server.session.DefaultDispatchContext;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class DefaultTaskRegistryTest {

    private final ServerEngine engine = newEngine(b -> {});
    private final DefaultTaskRegistry registry =
            new DefaultTaskRegistry(engine, TasksConfig.builder().build());
    private final HashMap<String, RpcMethodHandler<?, ?>> handlers = new HashMap<>();

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @BeforeEach
    void setUp() {
        TaskMethodHandlers.register(handlers, registry);
    }

    @Test
    void listTasksReturnsEmptyList() throws Exception {
        var listHandler = handlers.get("tasks/list");
        var result = decodeAndHandle(listHandler, DefaultDispatchContext.noop(), null);
        assertThat(result).isInstanceOf(ListTasksResult.class);
        var listResult = (ListTasksResult) result;
        assertThat(listResult.tasks()).isEmpty();
    }

    @Test
    void listWithZeroLimitUsesDefaultPageSize() {
        registry.add(TaskEntry.builder("id-a").build());
        registry.add(TaskEntry.builder("id-b").build());
        var result = registry.list(0, null);
        assertThat(result.items()).hasSize(2);
    }

    @Test
    void listWithCursorSkipsPastCursor() {
        registry.add(TaskEntry.builder("id-alpha").build());
        registry.add(TaskEntry.builder("id-beta").build());
        registry.add(TaskEntry.builder("id-gamma").build());
        var result = registry.list(1, "id-alpha");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().id()).isEqualTo("id-beta");
    }

    @Test
    void listReturnsCursorWhenMoreItemsAvailable() {
        registry.add(TaskEntry.builder("id-a").build());
        registry.add(TaskEntry.builder("id-b").build());
        var result = registry.list(1, null);
        assertThat(result.nextCursor()).isEqualTo("id-a");
    }

    @Test
    void listReturnsNullCursorWhenAllItemsReturned() {
        registry.add(TaskEntry.builder("id-a").build());
        var result = registry.list(10, null);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void listWithCustomPageSize() {
        try (var engine = newEngine(b -> {})) {
            var reg = new DefaultTaskRegistry(
                    engine, TasksConfig.builder().pageSize(1).build());
            reg.add(TaskEntry.builder("id-a").build());
            reg.add(TaskEntry.builder("id-b").build());
            var result = reg.list(0, null);
            assertThat(result.items()).hasSize(1);
            assertThat(result.nextCursor()).isEqualTo("id-a");
        }
    }

    @Test
    void listTasksReturnsRegisteredTasks() throws Exception {
        registry.add(TaskEntry.builder("1").build());
        registry.add(TaskEntry.builder("2").build());

        var listHandler = handlers.get("tasks/list");
        var result = (ListTasksResult) decodeAndHandle(listHandler, DefaultDispatchContext.noop(), null);
        assertThat(result.tasks()).hasSize(2);
    }

    @Test
    void getTaskNotFound() throws Exception {
        var getHandler = handlers.get("tasks/get");
        var result = decodeAndHandle(getHandler, DefaultDispatchContext.noop(), Map.of("taskId", "nonexistent"));
        assertThat(result).isInstanceOf(ServerError.class);
        var err = (ServerError) result;
        assertThat(err.kind()).isEqualTo(ServerError.Kind.INVALID_PARAMS);
    }

    @Test
    void getTaskReturnsResult() throws Exception {
        registry.add(TaskEntry.builder("task-1").build());

        var getHandler = handlers.get("tasks/get");
        var result = decodeAndHandle(getHandler, DefaultDispatchContext.noop(), Map.of("taskId", "task-1"));
        assertThat(result).isInstanceOf(GetTaskResult.class);
        var getResult = (GetTaskResult) result;
        assertThat(getResult.taskId()).isEqualTo("task-1");
        assertThat(getResult.status()).isNotNull();
    }

    @Test
    void getTaskMissingId() {
        var getHandler = handlers.get("tasks/get");
        assertThatThrownBy(() -> decodeAndHandle(getHandler, DefaultDispatchContext.noop(), Map.of()))
                .isInstanceOf(RequestMappingException.class)
                .extracting(e -> ((RequestMappingException) e).error().kind())
                .isEqualTo(ServerError.Kind.INVALID_PARAMS);
    }

    @Test
    void cancelTaskReturnsCancelTaskResult() throws Exception {
        registry.add(TaskEntry.builder("task-1").build());

        var cancelHandler = handlers.get("tasks/cancel");
        var result = decodeAndHandle(cancelHandler, DefaultDispatchContext.noop(), Map.of("taskId", "task-1"));
        assertThat(result).isInstanceOf(CancelTaskResult.class);
        var cancelResult = (CancelTaskResult) result;
        assertThat(cancelResult.taskId()).isEqualTo("task-1");
        assertThat(cancelResult.status())
                .isEqualTo(dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.TaskStatus.CANCELLED);
    }

    @Test
    void cancelNonExistentTaskReturnsError() throws Exception {
        var cancelHandler = handlers.get("tasks/cancel");
        var result = decodeAndHandle(cancelHandler, DefaultDispatchContext.noop(), Map.of("taskId", "nonexistent"));
        assertThat(result).isInstanceOf(ServerError.class);
    }

    @Test
    void taskResultNotFoundReturnsError() throws Exception {
        var resultHandler = handlers.get("tasks/result");
        var result = decodeAndHandle(resultHandler, DefaultDispatchContext.noop(), Map.of("taskId", "task-1"));
        assertThat(result).isInstanceOf(ServerError.class);
    }

    @Test
    @Timeout(5)
    void taskResultBlocksUntilTaskReachesTerminalState() throws Exception {
        registry.add(TaskEntry.builder("task-1").build());
        var resultHandler = handlers.get("tasks/result");

        // SEP-1686: tasks/result MUST block a working task until it is terminal. Complete it from
        // another thread; the handler call below must unblock and return the terminal payload.
        var completer = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            registry.completeTask("task-1", "{\"result\":\"ok\"}");
        });
        completer.start();

        var result = decodeAndHandle(resultHandler, DefaultDispatchContext.noop(), Map.of("taskId", "task-1"));
        completer.join();

        assertThat(result).isInstanceOf(CallToolResult.class);
        assertThat(((CallToolResult) result).isError()).isNull();
    }

    @Test
    void taskResultCompletedTaskReturnsPayload() throws Exception {
        registry.add(TaskEntry.builder("task-1").build());
        registry.completeTask("task-1", "{\"result\":\"ok\"}");

        var resultHandler = handlers.get("tasks/result");
        var result = decodeAndHandle(resultHandler, DefaultDispatchContext.noop(), Map.of("taskId", "task-1"));

        assertThat(result).isInstanceOf(CallToolResult.class);
        var payload = (CallToolResult) result;
        assertThat(payload.content()).isNotEmpty();
        assertThat(payload.isError()).isNull();
        assertThat(payload._meta()).containsKey("io.modelcontextprotocol/related-task");
    }

    @Test
    void createTaskViaRegistry() throws Exception {
        var entry = registry.create();
        assertThat(entry.id()).isNotNull();
        assertThat(entry.status()).isEqualTo(TaskState.SUBMITTED);

        var listHandler = handlers.get("tasks/list");
        var listResult = (ListTasksResult) decodeAndHandle(listHandler, DefaultDispatchContext.noop(), null);
        assertThat(listResult.tasks()).hasSize(1);
    }

    @Test
    void createWithCallerSuppliedIdUsesThatId() throws Exception {
        var entry = registry.create(TaskOptions.builder().id("my-task-1").build());
        assertThat(entry.id()).isEqualTo("my-task-1");

        var getHandler = handlers.get("tasks/get");
        var result = (GetTaskResult)
                decodeAndHandle(getHandler, DefaultDispatchContext.noop(), Map.of("taskId", "my-task-1"));
        assertThat(result.taskId()).isEqualTo("my-task-1");
    }

    @Test
    void createWithDuplicateIdIsRejected() {
        registry.create(TaskOptions.builder().id("dup-1").build());

        assertThatThrownBy(
                        () -> registry.create(TaskOptions.builder().id("dup-1").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup-1");
    }

    @Test
    void completeTaskViaRegistry() throws Exception {
        var entry = registry.create();
        var completed = registry.completeTask(entry.id(), "{\"result\":\"done\"}");
        assertThat(completed).isTrue();

        var getHandler = handlers.get("tasks/get");
        var result = (GetTaskResult)
                decodeAndHandle(getHandler, DefaultDispatchContext.noop(), Map.of("taskId", entry.id()));
        assertThat(result.status()).isEqualTo(dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.TaskStatus.COMPLETED);
    }

    @Test
    void failTaskViaRegistry() throws Exception {
        var entry = registry.create();
        var failed = registry.failTask(entry.id(), "{\"error\":\"something went wrong\"}");
        assertThat(failed).isTrue();

        var getHandler = handlers.get("tasks/get");
        var result = (GetTaskResult)
                decodeAndHandle(getHandler, DefaultDispatchContext.noop(), Map.of("taskId", entry.id()));
        assertThat(result.status()).isEqualTo(dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.TaskStatus.FAILED);
    }

    @Test
    void taskUpdateFailureMarksTaskFailedAndUnregistersResumer() throws Exception {
        var task = (TaskEntry) registry.create();
        assertThat(task.start()).isTrue();
        task.requireInput(
                new InputRequestBundle(
                        Map.of("user_name", FormInputRequest.of("What is your name?", JsonSchema.objectSchema())),
                        null),
                null);
        registry.registerResumer(task.id(), (context, responses, state) -> {
            throw new IllegalStateException("boom");
        });
        var context = DefaultDispatchContext.create(
                Protocols.list().stream()
                        .filter(protocol -> protocol.versionString().equals("2026-07-28"))
                        .findFirst()
                        .orElseThrow(),
                engine);
        context.enableExtension(TasksExtension.ID);

        var result = decodeAndHandle(
                handlers.get("tasks/update"),
                context,
                Map.of("taskId", task.id(), "inputResponses", Map.of("user_name", "Alice")));

        assertThat(result).isNotInstanceOf(ServerError.class);
        assertThat(task.status()).isEqualTo(TaskState.FAILED);
        assertThat(registry.findResumer(task.id())).isNull();
    }

    @Test
    void aCallerOwnedTaskDeliversSubmittedInputToItsOwnResumer() throws Exception {
        // Given a task the server created itself, with a resumer to receive the answers
        var delivered = new AtomicReference<Map<String, Object>>();
        var deliveredState = new AtomicReference<String>();
        var task = registry.create(TaskOptions.builder().id("owned-1").build(), (resumed, responses, state) -> {
            delivered.set(responses);
            deliveredState.set(state);
            resumed.complete(TaskResult.completed(Map.of("greeted", responses.get("user_name"))));
        });
        assertThat(task.start()).isTrue();
        assertThat(task.requireInput(
                        new InputRequestBundle(
                                Map.of("user_name", FormInputRequest.of("Name?", JsonSchema.objectSchema())),
                                "round-1"),
                        null))
                .isTrue();

        // When the client answers it via tasks/update
        var result = decodeAndHandle(
                handlers.get("tasks/update"),
                modernTasksContext(),
                Map.of("taskId", "owned-1", "inputResponses", Map.of("user_name", "Alice")));

        // Then the resumer -- not a tool handler -- receives the answers and drives the task home
        assertThat(result).isNotInstanceOf(ServerError.class);
        assertThat(delivered.get()).isEqualTo(Map.of("user_name", "Alice"));
        assertThat(deliveredState.get()).isEqualTo("round-1");
        assertThat(task.status()).isEqualTo(TaskState.COMPLETED);
    }

    @Test
    void reportProgressOnATaskWithoutAProgressTokenIsSilentlyDropped() {
        // Given a task created with no progress token (the caller never opted in)
        var task = registry.create();
        task.start();

        // When progress is reported, it must not blow up -- there is simply nobody to notify
        task.reportProgress(0.5, 1.0, "halfway");

        assertThat(task.status()).isEqualTo(TaskState.WORKING);
    }

    private DispatchContext modernTasksContext() {
        var context = DefaultDispatchContext.create(
                Protocols.list().stream()
                        .filter(protocol -> protocol.versionString().equals("2026-07-28"))
                        .findFirst()
                        .orElseThrow(),
                engine);
        context.enableExtension(TasksExtension.ID);
        return context;
    }

    @Test
    void createWithKeepAliveOverrideAppliesToEntry() {
        var entry = (TaskEntry) registry.create(
                TaskOptions.builder().keepAlive(Duration.ofSeconds(1)).build());
        assertThat(entry.keepAlive()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void runJanitorSweepDropsExpiredTerminalTaskResult() throws Exception {
        var entry = (TaskEntry) registry.create(
                TaskOptions.builder().keepAlive(Duration.ofMillis(10)).build());
        registry.completeTask(entry.id(), "{\"ok\":true}");

        var deadline = System.currentTimeMillis() + 500;
        while (!entry.isResultExpired() && System.currentTimeMillis() < deadline) {
            Thread.sleep(1);
        }
        registry.runJanitorSweep();

        assertThat(registry.get(entry.id())).isNull();
    }

    @Test
    void updateStatusFromClientNotification() {
        registry.add(TaskEntry.builder("task-1").build());

        var result = registry.updateStatus("task-1", TaskState.INPUT_REQUIRED, "Need more info");

        assertThat(result).isTrue();
        var entry = registry.getById("task-1");
        assertThat(entry.status()).isEqualTo(TaskState.INPUT_REQUIRED);
    }

    @Test
    void updateStatusFromClientNotificationUnknownTask() {
        var result = registry.updateStatus("nonexistent", TaskState.COMPLETED, null);

        assertThat(result).isFalse();
    }

    @Test
    void updateStatusFromClientNotificationInvalidTransition() {
        registry.add(TaskEntry.builder("task-1").build());
        registry.completeTask("task-1", "{\"ok\":true}");

        var result = registry.updateStatus("task-1", TaskState.WORKING, null);

        assertThat(result).isFalse();
    }

    @Test
    void startTtlJanitorSafely() {
        registry.startTtlJanitor();
        registry.startTtlJanitor();
        registry.stopTtlJanitor();
    }

    @Test
    void stopTtlJanitorBeforeStart() {
        registry.stopTtlJanitor();
    }

    @Test
    void shouldFireOnChangeWhenTaskAdded() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.add(TaskEntry.builder("id-x").build());

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldFireOnChangeWhenExistingTaskRemoved() {
        // Default single-arg constructor starts WORKING (non-terminal), so remove() cancels
        // it first (1 onChange) before removing it from the index (2nd onChange).
        registry.add(TaskEntry.builder("id-x").build());

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        assertThat(registry.remove("id-x")).isTrue();

        assertThat(callCount).hasValue(2);
    }

    @Test
    void shouldNotFireOnChangeWhenRemovingNonExistentTask() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        assertThat(registry.remove("does-not-exist")).isFalse();

        assertThat(callCount).hasValue(0);
    }

    @Test
    void removeOfTerminalTaskSkipsCancelAndFiresOnChangeOnce() {
        var entry = (TaskEntry) registry.create();
        registry.completeTask(entry.id(), "{\"ok\":true}");

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        assertThat(registry.remove(entry.id())).isTrue();

        assertThat(callCount).hasValue(1);
        assertThat(registry.get(entry.id())).isNull();
    }

    @Test
    void removeOfActiveTaskCancelsFirst() {
        var entry = (TaskEntry) registry.create();
        registry.updateStatus(entry.id(), TaskState.WORKING, null);

        assertThat(registry.remove(entry.id())).isTrue();

        assertThat(entry.status()).isEqualTo(TaskState.CANCELLED);
        assertThat(registry.get(entry.id())).isNull();
    }

    @Test
    @Timeout(5)
    void taskResultCancelledWhileTheCallerIsBlockedOnItReportsCancellationNotAGenericFailure() throws Exception {
        registry.add(TaskEntry.builder("task-1").build());
        var resultHandler = handlers.get("tasks/result");
        var result = new AtomicReference<Object>();

        // SEP-1686: tasks/result MUST block a working task until it is terminal. Run the blocking
        // call on its own thread and cancel the task only once that thread is genuinely parked in
        // CompletableFuture.join() -- proven by its Thread.State, not assumed via a sleep -- so the
        // handler is guaranteed to already be past its own status() == CANCELLED pre-check and must
        // resolve the race through the join() catch block instead.
        var joiner = new Thread(() -> {
            try {
                result.set(decodeAndHandle(resultHandler, DefaultDispatchContext.noop(), Map.of("taskId", "task-1")));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        joiner.start();
        awaitParked(joiner);

        registry.getAndCancelTask("task-1");
        joiner.join();

        assertThat(result.get()).isInstanceOf(ServerError.class);
        var error = (ServerError) result.get();
        assertThat(error.kind()).isEqualTo(ServerError.Kind.INVALID_PARAMS);
        assertThat(error.message()).isEqualTo("Task was cancelled");
    }

    /** Busy-waits until {@code thread} is parked in a blocking call, e.g. {@code CompletableFuture.join()}. */
    private static void awaitParked(Thread thread) {
        while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
            Thread.onSpinWait();
        }
    }
}
