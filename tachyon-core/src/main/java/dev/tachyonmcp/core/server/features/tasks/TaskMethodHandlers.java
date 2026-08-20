/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.protocol.ProtocolRequestMapper;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JSON-RPC adapters for task operations. */
public final class TaskMethodHandlers {

    private TaskMethodHandlers() {}

    public static void register(Map<String, RpcMethodHandler> handlers, DefaultTaskRegistry registry) {
        handlers.put("tasks/list", new TasksListHandler(registry));
        handlers.put("tasks/get", new TasksGetHandler(registry));
        handlers.put("tasks/cancel", new TasksCancelHandler(registry));
        handlers.put("tasks/result", new TasksResultHandler(registry));
        handlers.put("tasks/update", new TasksUpdateHandler(registry));
    }

    /**
     * MCP 2026-07-28 (SEP-2663) reserves no scoping mechanism for {@code tasks/list} (a poorly-
     * scoped list could leak one caller's tasks to another) and replaces {@code tasks/result} with
     * the outcome inlined into {@code tasks/get} -- both methods are removed under that protocol
     * version. MCP 2025-11-25's legacy task model predates the extension and keeps both.
     */
    private static @Nullable ServerError legacyTasksUnavailable(DispatchContext context) {
        return context.requestMapper().supportsLegacyTaskAugmentation()
                ? null
                : ServerErrors.methodNotFound("Method not found");
    }

    /**
     * Inverse of {@link #legacyTasksUnavailable}: {@code tasks/update} is SEP-2663's ext-tasks
     * method for submitting a paused task's input responses -- it has no wire shape under
     * 2025-11-25's earlier, unrelated task model (no such field on that version's generated
     * {@code Task}/{@code GetTaskResult} models), so it doesn't exist there at all.
     */
    private static @Nullable ServerError modernTasksOnly(DispatchContext context) {
        return context.requestMapper().supportsLegacyTaskAugmentation()
                ? ServerErrors.methodNotFound("Method not found")
                : null;
    }

    private record TasksListHandler(DefaultTaskRegistry registry) implements RpcMethodHandler {
        @Override
        public String method() {
            return "tasks/list";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var unavailable = legacyTasksUnavailable(context);
            if (unavailable != null) return unavailable;
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
            var unavailable = legacyTasksUnavailable(context);
            if (unavailable != null) return unavailable;
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

    private record TasksUpdateHandler(DefaultTaskRegistry registry) implements RpcMethodHandler {
        private static final Logger logger = LoggerFactory.getLogger(TasksUpdateHandler.class);

        @Override
        public String method() {
            return "tasks/update";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var versionGate = modernTasksOnly(context);
            if (versionGate != null) return versionGate;
            var missingCapability = TasksExtension.requireDeclared(context);
            if (missingCapability != null) return missingCapability;

            final ProtocolRequestMapper.TaskUpdateRequest request;
            try {
                request = context.requestMapper().taskUpdate(params);
            } catch (RequestMappingException e) {
                return e.error();
            }
            var entry = registry.getById(request.taskId());
            if (entry == null) {
                return ServerErrors.invalidParams("Failed to retrieve task: Task not found");
            }

            var resumeInputs = entry.submitInput(request.inputResponses());
            if (resumeInputs != null) {
                var resumer = registry.getResumer(entry.id());
                if (resumer != null) {
                    try {
                        resumer.resume(resumeInputs.inputResponses(), resumeInputs.requestState());
                    } catch (RuntimeException e) {
                        logger.error("Task resumer failed for taskId={}", entry.id(), e);
                    }
                }
                // else: a hand-created task (server.tasks().create() + task.requireInput(...)
                // called directly by user code, no tool-augmented dispatch) has no resumer -- it's
                // already transitioned to WORKING by submitInput(); its owner drives it forward.
            }
            return context.responseMapper().emptyResult();
        }
    }
}
