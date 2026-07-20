/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import static dev.tachyonmcp.test.TestUtils.newEngine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CallToolResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CancelTaskResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.GetTaskResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListTasksResult;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.config.TasksConfig;
import dev.tachyonmcp.server.domain.TaskResult;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.server.session.DefaultDispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcError;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class TaskRegistryTest {

    private final ServerEngine engine = newEngine(b -> {});
    private final DefaultTaskRegistry registry =
            new DefaultTaskRegistry(engine, TasksConfig.builder().build());
    private final HashMap<String, RpcMethodHandler> handlers = new HashMap<>();

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @BeforeEach
    void setUp() {
        registry.registerHandlers(handlers);
    }

    @Test
    void listTasksReturnsEmptyList() throws Exception {
        var listHandler = handlers.get("tasks/list");
        var result = listHandler.handle(DefaultDispatchContext.noop(), null);
        assertThat(result).isInstanceOf(ListTasksResult.class);
        var listResult = (ListTasksResult) result;
        assertThat(listResult.tasks()).isEmpty();
    }

    @Test
    void listWithZeroLimitUsesDefaultPageSize() {
        registry.add(new TaskEntry("id-a", "A"));
        registry.add(new TaskEntry("id-b", "B"));
        var result = registry.list(0, null);
        assertThat(result.items()).hasSize(2);
    }

    @Test
    void listWithCursorSkipsPastCursor() {
        registry.add(new TaskEntry("id-alpha", "Alpha"));
        registry.add(new TaskEntry("id-beta", "Beta"));
        registry.add(new TaskEntry("id-gamma", "Gamma"));
        var result = registry.list(1, "id-alpha");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().id()).isEqualTo("id-beta");
    }

    @Test
    void listReturnsCursorWhenMoreItemsAvailable() {
        registry.add(new TaskEntry("id-a", "A"));
        registry.add(new TaskEntry("id-b", "B"));
        var result = registry.list(1, null);
        assertThat(result.nextCursor()).isEqualTo("id-a");
    }

    @Test
    void listReturnsNullCursorWhenAllItemsReturned() {
        registry.add(new TaskEntry("id-a", "A"));
        var result = registry.list(10, null);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void listWithCustomPageSize() {
        try (var engine = newEngine(b -> {})) {
            var reg = new DefaultTaskRegistry(
                    engine, TasksConfig.builder().pageSize(1).build());
            reg.add(new TaskEntry("id-a", "A"));
            reg.add(new TaskEntry("id-b", "B"));
            var result = reg.list(0, null);
            assertThat(result.items()).hasSize(1);
            assertThat(result.nextCursor()).isEqualTo("id-a");
        }
    }

    @Test
    void listTasksReturnsRegisteredTasks() throws Exception {
        registry.add(new TaskEntry("1", "First task"));
        registry.add(new TaskEntry("2", "Second task"));

        var listHandler = handlers.get("tasks/list");
        var result = (ListTasksResult) listHandler.handle(DefaultDispatchContext.noop(), null);
        assertThat(result.tasks()).hasSize(2);
    }

    @Test
    void getTaskNotFound() throws Exception {
        var getHandler = handlers.get("tasks/get");
        var result = getHandler.handle(DefaultDispatchContext.noop(), Map.of("taskId", "nonexistent"));
        assertThat(result).isInstanceOf(JsonRpcError.class);
        var err = (JsonRpcError) result;
        assertThat(err.code()).isEqualTo(JsonRpcErrors.INVALID_PARAMS);
    }

    @Test
    void getTaskReturnsResult() throws Exception {
        registry.add(new TaskEntry("task-1", "A task"));

        var getHandler = handlers.get("tasks/get");
        var result = getHandler.handle(DefaultDispatchContext.noop(), Map.of("taskId", "task-1"));
        assertThat(result).isInstanceOf(GetTaskResult.class);
        var getResult = (GetTaskResult) result;
        assertThat(getResult.taskId()).isEqualTo("task-1");
        assertThat(getResult.status()).isNotNull();
    }

    @Test
    void getTaskMissingId() throws Exception {
        var getHandler = handlers.get("tasks/get");
        var result = getHandler.handle(DefaultDispatchContext.noop(), Map.of());
        assertThat(result).isInstanceOf(JsonRpcError.class);
    }

    @Test
    void cancelTaskReturnsCancelTaskResult() throws Exception {
        registry.add(new TaskEntry("task-1", "A task"));

        var cancelHandler = handlers.get("tasks/cancel");
        var result = cancelHandler.handle(DefaultDispatchContext.noop(), Map.of("taskId", "task-1"));
        assertThat(result).isInstanceOf(CancelTaskResult.class);
        var cancelResult = (CancelTaskResult) result;
        assertThat(cancelResult.taskId()).isEqualTo("task-1");
        assertThat(cancelResult.status())
                .isEqualTo(dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus.CANCELLED);
    }

    @Test
    void cancelNonExistentTaskReturnsError() throws Exception {
        var cancelHandler = handlers.get("tasks/cancel");
        var result = cancelHandler.handle(DefaultDispatchContext.noop(), Map.of("taskId", "nonexistent"));
        assertThat(result).isInstanceOf(JsonRpcError.class);
    }

    @Test
    void taskResultNotFoundReturnsError() throws Exception {
        var resultHandler = handlers.get("tasks/result");
        var result = resultHandler.handle(DefaultDispatchContext.noop(), Map.of("taskId", "task-1"));
        assertThat(result).isInstanceOf(JsonRpcError.class);
    }

    @Test
    @Timeout(5)
    void taskResultBlocksUntilTaskReachesTerminalState() throws Exception {
        registry.add(new TaskEntry("task-1", "A task"));
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

        var result = resultHandler.handle(DefaultDispatchContext.noop(), Map.of("taskId", "task-1"));
        completer.join();

        assertThat(result).isInstanceOf(CallToolResult.class);
        assertThat(((CallToolResult) result).isError()).isNull();
    }

    @Test
    void taskResultCompletedTaskReturnsPayload() throws Exception {
        registry.add(new TaskEntry("task-1", "A task"));
        registry.completeTask("task-1", "{\"result\":\"ok\"}");

        var resultHandler = handlers.get("tasks/result");
        var result = resultHandler.handle(DefaultDispatchContext.noop(), Map.of("taskId", "task-1"));

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
        var listResult = (ListTasksResult) listHandler.handle(DefaultDispatchContext.noop(), null);
        assertThat(listResult.tasks()).hasSize(1);
    }

    @Test
    void createWithCallerSuppliedIdUsesThatId() throws Exception {
        var entry = registry.create(TaskOptions.builder().id("my-task-1").build());
        assertThat(entry.id()).isEqualTo("my-task-1");

        var getHandler = handlers.get("tasks/get");
        var result = (GetTaskResult) getHandler.handle(DefaultDispatchContext.noop(), Map.of("taskId", "my-task-1"));
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
        var result = (GetTaskResult) getHandler.handle(DefaultDispatchContext.noop(), Map.of("taskId", entry.id()));
        assertThat(result.status()).isEqualTo(dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus.COMPLETED);
    }

    @Test
    void failTaskViaRegistry() throws Exception {
        var entry = registry.create();
        var failed = registry.failTask(entry.id(), "{\"error\":\"something went wrong\"}");
        assertThat(failed).isTrue();

        var getHandler = handlers.get("tasks/get");
        var result = (GetTaskResult) getHandler.handle(DefaultDispatchContext.noop(), Map.of("taskId", entry.id()));
        assertThat(result.status()).isEqualTo(dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus.FAILED);
    }

    @Test
    void statusTransitionValidation() {
        var entry = (TaskEntry) registry.create();
        assertThat(entry.transitionTo(TaskState.COMPLETED, new TaskResult.Completed(null)))
                .isTrue();
        assertThat(entry.transitionTo(TaskState.FAILED)).isFalse();
        assertThat(entry.transitionTo(TaskState.WORKING)).isFalse();
    }

    @Test
    void taskNotExpiredWithoutTtl() {
        var entry = new TaskEntry("exp-1", "Expiring task");
        assertThat(entry.isExpired()).isFalse();
    }

    @Test
    void taskExpiresAfterTtl() throws Exception {
        var entry = new TaskEntry(
                TaskDescriptor.builder().id("exp-1").build(),
                "exp-1",
                TaskState.WORKING,
                Duration.ofMillis(10),
                null,
                null,
                null);
        var deadline = System.currentTimeMillis() + 500;
        while (!entry.isExpired() && System.currentTimeMillis() < deadline) {
            Thread.sleep(1);
        }
        assertThat(entry.isExpired()).isTrue();
    }

    @Test
    void taskResultNotExpiredBeforeKeepAliveElapses() {
        var entry = new TaskEntry(
                TaskDescriptor.builder().id("res-1").build(),
                "res-1",
                TaskState.WORKING,
                null,
                null,
                null,
                null,
                Duration.ofMillis(200));
        entry.complete(new TaskResult.Completed(null));
        assertThat(entry.isResultExpired()).isFalse();
    }

    @Test
    void taskResultExpiresAfterKeepAlive() throws Exception {
        var entry = new TaskEntry(
                TaskDescriptor.builder().id("res-2").build(),
                "res-2",
                TaskState.WORKING,
                null,
                null,
                null,
                null,
                Duration.ofMillis(10));
        entry.complete(new TaskResult.Completed(null));

        var deadline = System.currentTimeMillis() + 500;
        while (!entry.isResultExpired() && System.currentTimeMillis() < deadline) {
            Thread.sleep(1);
        }
        assertThat(entry.isResultExpired()).isTrue();
    }

    @Test
    void taskResultNeverExpiresWithZeroKeepAlive() {
        var entry = new TaskEntry(
                TaskDescriptor.builder().id("res-3").build(),
                "res-3",
                TaskState.WORKING,
                null,
                null,
                null,
                null,
                Duration.ZERO);
        entry.complete(new TaskResult.Completed(null));
        assertThat(entry.isResultExpired()).isFalse();
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
    void statusEnumFsmTerminalStates() {
        assertThat(TaskState.COMPLETED.isTerminal()).isTrue();
        assertThat(TaskState.FAILED.isTerminal()).isTrue();
        assertThat(TaskState.CANCELLED.isTerminal()).isTrue();
        assertThat(TaskState.WORKING.isTerminal()).isFalse();
        assertThat(TaskState.INPUT_REQUIRED.isTerminal()).isFalse();
    }

    @Test
    void statusEnumFsmActiveStates() {
        assertThat(TaskState.WORKING.isActive()).isTrue();
        assertThat(TaskState.INPUT_REQUIRED.isActive()).isTrue();
        assertThat(TaskState.COMPLETED.isActive()).isFalse();
        assertThat(TaskState.FAILED.isActive()).isFalse();
        assertThat(TaskState.CANCELLED.isActive()).isFalse();
    }

    @Test
    void statusEnumFsmTransitions() {
        assertThat(TaskState.WORKING.canTransitionTo(TaskState.COMPLETED)).isTrue();
        assertThat(TaskState.WORKING.canTransitionTo(TaskState.CANCELLED)).isTrue();
        assertThat(TaskState.WORKING.canTransitionTo(TaskState.INPUT_REQUIRED)).isTrue();
        assertThat(TaskState.COMPLETED.canTransitionTo(TaskState.WORKING)).isFalse();
        assertThat(TaskState.CANCELLED.canTransitionTo(TaskState.WORKING)).isFalse();
        assertThat(TaskState.FAILED.canTransitionTo(TaskState.WORKING)).isFalse();
    }

    @Test
    void updateStatusFromClientNotification() {
        registry.add(new TaskEntry("task-1", "A task"));

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
        registry.add(new TaskEntry("task-1", "A task"));
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

        registry.add(new TaskEntry("id-x", "A task"));

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldFireOnChangeWhenExistingTaskRemoved() {
        registry.add(new TaskEntry("id-x", "A task"));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.remove("id-x");

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldNotFireOnChangeWhenRemovingNonExistentTask() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.remove("does-not-exist");

        assertThat(callCount).hasValue(0);
    }
}
