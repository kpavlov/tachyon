/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.Map;

/** JSON-RPC adapters for task operations. */
public final class TaskMethodHandlers {

    private TaskMethodHandlers() {}

    public static void register(Map<String, RpcMethodHandler> handlers, DefaultTaskRegistry registry) {
        handlers.put("tasks/list", new TasksListHandler(registry));
        handlers.put("tasks/get", new TasksGetHandler(registry));
        handlers.put("tasks/cancel", new TasksCancelHandler(registry));
        handlers.put("tasks/result", new TasksResultHandler(registry));
    }

    private record TasksListHandler(DefaultTaskRegistry registry) implements RpcMethodHandler {
        @Override
        public String method() {
            return "tasks/list";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var page = context.requestMapper().page(params);
            var paginated = registry.listEntries(page.limit(), page.cursor());
            if (!paginated.cursorValid()) {
                return ServerErrors.invalidParams("Invalid cursor");
            }
            return context.responseMapper().listTasksResult(paginated.items(), paginated.nextCursor());
        }
    }

    private record TasksGetHandler(DefaultTaskRegistry registry) implements RpcMethodHandler {
        @Override
        public String method() {
            return "tasks/get";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var missingCapability = TasksExtension.requireDeclared(context);
            if (missingCapability != null) return missingCapability;
            final String taskId;
            try {
                taskId = context.requestMapper().taskId(params);
            } catch (RequestMappingException e) {
                return e.error();
            }
            var entry = registry.getById(taskId);
            return entry != null
                    ? context.responseMapper().getTaskResult(entry)
                    : ServerErrors.invalidParams("Failed to retrieve task: Task not found");
        }
    }

    private record TasksCancelHandler(DefaultTaskRegistry registry) implements RpcMethodHandler {
        @Override
        public String method() {
            return "tasks/cancel";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var missingCapability = TasksExtension.requireDeclared(context);
            if (missingCapability != null) return missingCapability;
            final String taskId;
            try {
                taskId = context.requestMapper().taskId(params);
            } catch (RequestMappingException e) {
                return e.error();
            }
            var task = registry.getAndCancelTask(taskId);
            if (task == null) {
                return ServerErrors.invalidParams("Failed to retrieve task: Task not found");
            }
            if (task.status() != TaskState.CANCELLED) {
                return ServerErrors.invalidParams("Task cannot be cancelled in current state: " + task.status());
            }
            return context.responseMapper().cancelTaskResult(task);
        }
    }

    private record TasksResultHandler(DefaultTaskRegistry registry) implements RpcMethodHandler {
        @Override
        public String method() {
            return "tasks/result";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            final String taskId;
            try {
                taskId = context.requestMapper().taskId(params);
            } catch (RequestMappingException e) {
                return e.error();
            }
            var entry = registry.getById(taskId);
            if (entry == null) {
                return ServerErrors.invalidParams("Task not found");
            }
            var status = entry.status();
            if (status == TaskState.CANCELLED) {
                return ServerErrors.invalidParams("Task was cancelled");
            }
            if (status == TaskState.UNKNOWN) {
                return ServerErrors.invalidParams("Task is in unknown state");
            }
            if (status.isActive()) {
                try {
                    var result = entry.completion().toCompletableFuture().join();
                    return context.responseMapper().getTaskPayloadResult(result, entry.id());
                } catch (Exception e) {
                    return ServerErrors.invalidParams("Task result not available: " + e.getMessage());
                }
            }
            var result = entry.result();
            return result != null
                    ? context.responseMapper().getTaskPayloadResult(result, entry.id())
                    : ServerErrors.invalidParams("Task result not available");
        }
    }
}
