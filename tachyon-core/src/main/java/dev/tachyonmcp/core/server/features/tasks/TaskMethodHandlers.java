/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.features.tasks.LegacyTaskExecutionEngine;
import dev.tachyonmcp.api.server.features.tasks.TaskInput;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.protocol.ProtocolRequestMapper;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** JSON-RPC adapters for task operations. */
public final class TaskMethodHandlers {

    private TaskMethodHandlers() {}

    public static void register(Map<String, RpcMethodHandler<?, ?>> handlers, DefaultTaskRegistry registry) {
        handlers.put("tasks/list", new TasksListHandler(registry));
        handlers.put("tasks/get", new TasksGetHandler(registry));
        handlers.put("tasks/cancel", new TasksCancelHandler(registry));
        handlers.put("tasks/result", new TasksResultHandler(registry));
        handlers.put("tasks/update", new TasksUpdateHandler(registry));
    }

    private static @Nullable ServerError legacyTasksUnavailable(DispatchContext context) {
        return context.requestMapper().supportsLegacyTaskAugmentation()
                ? null
                : ServerErrors.methodNotFound("Method not found");
    }

    private static @Nullable ServerError modernTasksOnly(DispatchContext context) {
        return context.requestMapper().supportsLegacyTaskAugmentation()
                ? ServerErrors.methodNotFound("Method not found")
                : null;
    }

    private static void requireGate(@Nullable ServerError gate) {
        if (gate != null) {
            throw new RequestMappingException(gate);
        }
    }

    private record TasksListHandler(DefaultTaskRegistry registry)
            implements RpcMethodHandler<ProtocolRequestMapper.PageRequest, Object> {
        @Override
        public String method() {
            return "tasks/list";
        }

        @Override
        public ProtocolRequestMapper.PageRequest decode(DispatchContext context, @Nullable Object rawParams) {
            requireGate(legacyTasksUnavailable(context));
            return context.requestMapper().page(rawParams);
        }

        @Override
        public Object handle(DispatchContext context, ProtocolRequestMapper.PageRequest page) throws Exception {
            var engine = registry.taskExecutionEngine();
            if (!(engine instanceof LegacyTaskExecutionEngine legacy)) {
                return ServerErrors.methodNotFound("Method not found");
            }
            var result = legacy.list(context, registry.resolvePageLimit(page.limit()), page.cursor());
            if (!result.cursorValid()) {
                return ServerErrors.invalidParams("Invalid cursor");
            }
            return context.responseMapper().listTasksResult(result.items(), result.nextCursor());
        }
    }

    private record TasksGetHandler(DefaultTaskRegistry registry) implements RpcMethodHandler<String, Object> {
        @Override
        public String method() {
            return "tasks/get";
        }

        @Override
        public String decode(DispatchContext context, @Nullable Object rawParams) {
            requireGate(TasksExtension.requireDeclared(context));
            return context.requestMapper().taskId(rawParams);
        }

        @Override
        public Object handle(DispatchContext context, String taskId) throws Exception {
            var engine = registry.taskExecutionEngine();
            if (engine == null) {
                return ServerErrors.methodNotFound("Method not found");
            }
            var snapshot = engine.refresh(context, taskId);
            if (snapshot == null) {
                return ServerErrors.invalidParams("Failed to retrieve task: Task not found");
            }
            return context.responseMapper().getTaskResult(registry.publish(snapshot));
        }
    }

    private record TasksCancelHandler(DefaultTaskRegistry registry) implements RpcMethodHandler<String, Object> {
        @Override
        public String method() {
            return "tasks/cancel";
        }

        @Override
        public String decode(DispatchContext context, @Nullable Object rawParams) {
            requireGate(TasksExtension.requireDeclared(context));
            return context.requestMapper().taskId(rawParams);
        }

        @Override
        public Object handle(DispatchContext context, String taskId) throws Exception {
            var engine = registry.taskExecutionEngine();
            if (engine == null) {
                return ServerErrors.methodNotFound("Method not found");
            }
            var snapshot = engine.cancel(context, taskId);
            if (snapshot.status() != TaskState.CANCELLED) {
                return ServerErrors.invalidParams("Task cannot be cancelled in current state: " + snapshot.status());
            }
            return context.responseMapper().cancelTaskResult(registry.publish(snapshot));
        }
    }

    private record TasksResultHandler(DefaultTaskRegistry registry) implements RpcMethodHandler<String, Object> {
        @Override
        public String method() {
            return "tasks/result";
        }

        @Override
        public String decode(DispatchContext context, @Nullable Object rawParams) {
            requireGate(legacyTasksUnavailable(context));
            return context.requestMapper().taskId(rawParams);
        }

        @Override
        public Object handle(DispatchContext context, String taskId) throws Exception {
            var engine = registry.taskExecutionEngine();
            if (!(engine instanceof LegacyTaskExecutionEngine legacy)) {
                return ServerErrors.methodNotFound("Method not found");
            }
            var snapshot = registry.publish(legacy.awaitResult(context, taskId));
            return context.responseMapper().getTaskPayloadResult(snapshot.result(), snapshot.taskId());
        }
    }

    private record TasksUpdateHandler(DefaultTaskRegistry registry)
            implements RpcMethodHandler<ProtocolRequestMapper.TaskUpdateRequest, Object> {
        @Override
        public String method() {
            return "tasks/update";
        }

        @Override
        public ProtocolRequestMapper.TaskUpdateRequest decode(DispatchContext context, @Nullable Object rawParams) {
            requireGate(modernTasksOnly(context));
            requireGate(TasksExtension.requireDeclared(context));
            return context.requestMapper().taskUpdate(rawParams);
        }

        @Override
        public Object handle(DispatchContext context, ProtocolRequestMapper.TaskUpdateRequest request)
                throws Exception {
            var cached = registry.get(request.taskId());
            var input = TaskInput.builder()
                    .inputResponses(request.inputResponses())
                    .requestState(
                            cached != null && cached.pendingInput() != null
                                    ? cached.pendingInput().requestState()
                                    : null)
                    .build();
            var engine = registry.taskExecutionEngine();
            if (engine == null) {
                return ServerErrors.methodNotFound("Method not found");
            }
            engine.submitInput(context, request.taskId(), input);
            return context.responseMapper().emptyResult();
        }
    }
}
