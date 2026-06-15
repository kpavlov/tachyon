/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CancelTaskResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.GetTaskPayloadResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.GetTaskResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListTasksResult;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.TachyonMcpServer;
import dev.tachyonmcp.server.session.DefaultMcpContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcError;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskRegistryTest {

    private final TaskRegistry registry =
            new TaskRegistry(TachyonMcpServer.builder().build());
    private final HashMap<String, McpMethodHandler> handlers = new HashMap<>();

    @BeforeEach
    void setUp() {
        registry.registerHandlers(handlers);
    }

    @Test
    void listTasksReturnsEmptyList() throws Exception {
        var listHandler = handlers.get("tasks/list");
        var result = listHandler.handle(DefaultMcpContext.noop(), null);
        assertThat(result).isInstanceOf(ListTasksResult.class);
        var listResult = (ListTasksResult) result;
        assertThat(listResult.tasks()).isEmpty();
    }

    @Test
    void listTasksReturnsRegisteredTasks() throws Exception {
        registry.add(new TaskEntry("task-1", "1", "First task"));
        registry.add(new TaskEntry("task-2", "2", "Second task"));

        var listHandler = handlers.get("tasks/list");
        var result = (ListTasksResult) listHandler.handle(DefaultMcpContext.noop(), null);
        assertThat(result.tasks()).hasSize(2);
    }

    @Test
    void getTaskNotFound() throws Exception {
        var getHandler = handlers.get("tasks/get");
        var result = getHandler.handle(DefaultMcpContext.noop(), Map.of("taskId", "nonexistent"));
        assertThat(result).isInstanceOf(JsonRpcError.class);
        var err = (JsonRpcError) result;
        assertThat(err.code()).isEqualTo(JsonRpcErrors.INVALID_REQUEST);
    }

    @Test
    void getTaskReturnsResult() throws Exception {
        registry.add(new TaskEntry("my-task", "task-1", "A task"));

        var getHandler = handlers.get("tasks/get");
        var result = getHandler.handle(DefaultMcpContext.noop(), Map.of("taskId", "task-1"));
        assertThat(result).isInstanceOf(GetTaskResult.class);
        var getResult = (GetTaskResult) result;
        assertThat(getResult.taskId()).isEqualTo("task-1");
        assertThat(getResult.status()).isNotNull();
    }

    @Test
    void getTaskMissingId() throws Exception {
        var getHandler = handlers.get("tasks/get");
        var result = getHandler.handle(DefaultMcpContext.noop(), Map.of());
        assertThat(result).isInstanceOf(JsonRpcError.class);
    }

    @Test
    void cancelTaskReturnsCancelTaskResult() throws Exception {
        registry.add(new TaskEntry("my-task", "task-1", "A task"));

        var cancelHandler = handlers.get("tasks/cancel");
        var result = cancelHandler.handle(DefaultMcpContext.noop(), Map.of("taskId", "task-1"));
        assertThat(result).isInstanceOf(CancelTaskResult.class);
        var cancelResult = (CancelTaskResult) result;
        assertThat(cancelResult.taskId()).isEqualTo("task-1");
        assertThat(cancelResult.status())
                .isEqualTo(dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus.CANCELLED);
    }

    @Test
    void cancelNonExistentTaskReturnsError() throws Exception {
        var cancelHandler = handlers.get("tasks/cancel");
        var result = cancelHandler.handle(DefaultMcpContext.noop(), Map.of("taskId", "nonexistent"));
        assertThat(result).isInstanceOf(JsonRpcError.class);
    }

    @Test
    void taskResultNotFoundReturnsError() throws Exception {
        var resultHandler = handlers.get("tasks/result");
        var result = resultHandler.handle(DefaultMcpContext.noop(), Map.of("taskId", "task-1"));
        assertThat(result).isInstanceOf(JsonRpcError.class);
    }

    @Test
    void taskResultWorkingTaskReturnsError() throws Exception {
        registry.add(new TaskEntry("my-task", "task-1", "A task"));

        var resultHandler = handlers.get("tasks/result");
        var result = resultHandler.handle(DefaultMcpContext.noop(), Map.of("taskId", "task-1"));
        assertThat(result).isInstanceOf(JsonRpcError.class);
    }

    @Test
    void taskResultCompletedTaskReturnsPayload() throws Exception {
        registry.add(new TaskEntry("my-task", "task-1", "A task"));
        registry.completeTask("task-1", "{\"result\":\"ok\"}");

        var resultHandler = handlers.get("tasks/result");
        var result = resultHandler.handle(DefaultMcpContext.noop(), Map.of("taskId", "task-1"));
        assertThat(result).isInstanceOf(GetTaskPayloadResult.class);
    }

    @Test
    void createTaskViaRegistry() throws Exception {
        var entry = registry.createTask("test-task", "A test task");
        assertThat(entry.id()).isNotNull();
        assertThat(entry.status()).isEqualTo(TaskState.WORKING);

        var listHandler = handlers.get("tasks/list");
        var listResult = (ListTasksResult) listHandler.handle(DefaultMcpContext.noop(), null);
        assertThat(listResult.tasks()).hasSize(1);
    }

    @Test
    void completeTaskViaRegistry() throws Exception {
        var entry = registry.createTask("test-task", "A test task");
        var completed = registry.completeTask(entry.id(), "{\"result\":\"done\"}");
        assertThat(completed).isTrue();

        var getHandler = handlers.get("tasks/get");
        var result = (GetTaskResult) getHandler.handle(DefaultMcpContext.noop(), Map.of("taskId", entry.id()));
        assertThat(result.status()).isEqualTo(dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus.COMPLETED);
    }

    @Test
    void failTaskViaRegistry() throws Exception {
        var entry = registry.createTask("test-task", "A test task");
        var failed = registry.failTask(entry.id(), "{\"error\":\"something went wrong\"}");
        assertThat(failed).isTrue();

        var getHandler = handlers.get("tasks/get");
        var result = (GetTaskResult) getHandler.handle(DefaultMcpContext.noop(), Map.of("taskId", entry.id()));
        assertThat(result.status()).isEqualTo(dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus.FAILED);
    }

    @Test
    void statusTransitionValidation() {
        var entry = registry.createTask("test-task", "A test task");
        assertThat(entry.transitionTo(TaskState.COMPLETED)).isTrue();
        assertThat(entry.transitionTo(TaskState.FAILED)).isFalse();
        assertThat(entry.transitionTo(TaskState.WORKING)).isFalse();
    }

    @Test
    void taskNotExpiredWithoutTtl() {
        var entry = new TaskEntry("exp-task", "exp-1", "Expiring task");
        assertThat(entry.isExpired()).isFalse();
    }

    @Test
    void taskExpiresAfterTtl() throws Exception {
        var entry = new TaskEntry("exp-task", "exp-1", "Expiring task", TaskState.WORKING, 0.01);
        var deadline = System.currentTimeMillis() + 500;
        while (!entry.isExpired() && System.currentTimeMillis() < deadline) {
            Thread.sleep(1);
        }
        assertThat(entry.isExpired()).isTrue();
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
        registry.add(new TaskEntry("my-task", "task-1", "A task"));

        var result = registry.updateStatusFromClientNotification(
                "task-1", dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus.INPUT_REQUIRED, "Need more info");

        assertThat(result).isTrue();
        var entry = registry.getById("task-1");
        assertThat(entry.status()).isEqualTo(TaskState.INPUT_REQUIRED);
    }

    @Test
    void updateStatusFromClientNotificationUnknownTask() {
        var result = registry.updateStatusFromClientNotification(
                "nonexistent", dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus.COMPLETED, null);

        assertThat(result).isFalse();
    }

    @Test
    void updateStatusFromClientNotificationInvalidTransition() {
        registry.add(new TaskEntry("my-task", "task-1", "A task"));
        registry.completeTask("task-1", "{\"ok\":true}");

        var result = registry.updateStatusFromClientNotification(
                "task-1", dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus.WORKING, null);

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

        registry.add(new TaskEntry("task-x", "id-x", "A task"));

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldFireOnChangeWhenExistingTaskRemoved() {
        registry.add(new TaskEntry("task-x", "id-x", "A task"));

        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.remove("task-x");

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldNotFireOnChangeWhenRemovingNonExistentTask() {
        var callCount = new AtomicInteger(0);
        registry.onChange(callCount::incrementAndGet);

        registry.remove("does-not-exist");

        assertThat(callCount).hasValue(0);
    }

    @Test
    void shouldEvictOldByIdEntryWhenTaskWithSameNameAdded() {
        var first = new TaskEntry("worker", "id-first", "First");
        var second = new TaskEntry("worker", "id-second", "Second");

        registry.add(first);
        registry.add(second);

        assertThat(registry.getById("id-first")).isNull();
        assertThat(registry.getById("id-second")).isNotNull();
        assertThat(registry.getAll()).hasSize(1);
    }
}
