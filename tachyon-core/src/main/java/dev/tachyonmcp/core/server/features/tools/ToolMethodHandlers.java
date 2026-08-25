/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tools;

import static dev.tachyonmcp.core.server.domain.ServerErrors.fromUnhandledException;
import static dev.tachyonmcp.core.server.domain.ServerErrors.internalError;
import static dev.tachyonmcp.core.server.domain.ServerErrors.invalidParams;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.json.JsonSchemaValidator;
import dev.tachyonmcp.api.json.PayloadDeserializer;
import dev.tachyonmcp.api.json.PayloadSerializer;
import dev.tachyonmcp.api.json.SchemaValidationError;
import dev.tachyonmcp.api.server.domain.LoggingLevel;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.HandlerFutures;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.api.server.features.tasks.TaskSupport;
import dev.tachyonmcp.api.server.features.tools.ToolHandler;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.protocol.ProtocolRequestMapper;
import dev.tachyonmcp.core.server.OutboundSseStreamMessageRouter;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.features.tasks.TaskEntry;
import dev.tachyonmcp.core.server.features.tasks.TaskRegistry;
import dev.tachyonmcp.core.server.features.tasks.TasksExtension;
import dev.tachyonmcp.core.server.json.JsonUtils;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JSON-RPC adapters for tool operations. */
@InternalApi
public final class ToolMethodHandlers {

    private ToolMethodHandlers() {}

    public static void register(
            Map<String, RpcMethodHandler<?, ?>> handlers,
            DefaultToolRegistry registry,
            JsonSchemaValidator inputValidator,
            JsonSchemaValidator outputValidator,
            PayloadSerializer payloadSerializer,
            PayloadDeserializer payloadDeserializer) {
        handlers.put("tools/list", new ToolsListHandler(registry));
        handlers.put(
                "tools/call",
                new ToolsCallHandler(
                        registry, inputValidator, outputValidator, payloadSerializer, payloadDeserializer));
    }

    private record ToolsListHandler(DefaultToolRegistry registry)
            implements RpcMethodHandler<ProtocolRequestMapper.PageRequest, Object> {

        @Override
        public String method() {
            return "tools/list";
        }

        @Override
        public ProtocolRequestMapper.PageRequest decode(DispatchContext context, @Nullable Object rawParams) {
            return context.requestMapper().page(rawParams);
        }

        @Override
        public Object handle(DispatchContext context, ProtocolRequestMapper.PageRequest page) {
            var paginated = registry.list(page.limit(), page.cursor(), descriptor -> {
                var extensionId = descriptor.extensionId();
                return extensionId == null || context.isExtensionEnabled(extensionId);
            });
            if (!paginated.cursorValid()) return invalidParams("Invalid cursor");
            return context.responseMapper().listToolsResult(paginated.items(), paginated.nextCursor());
        }
    }

    private record ToolsCallHandler(
            DefaultToolRegistry registry,
            JsonSchemaValidator inputValidator,
            JsonSchemaValidator outputValidator,
            PayloadSerializer payloadSerializer,
            PayloadDeserializer payloadDeserializer)
            implements RpcMethodHandler<ProtocolRequestMapper.ToolCallRequest, Object> {

        private static final Logger logger = LoggerFactory.getLogger(ToolsCallHandler.class);

        @Override
        public String method() {
            return "tools/call";
        }

        @Override
        public ProtocolRequestMapper.ToolCallRequest decode(DispatchContext context, @Nullable Object rawParams) {
            return context.requestMapper().callTool(rawParams, payloadDeserializer);
        }

        @Override
        public @Nullable Object handle(DispatchContext context, ProtocolRequestMapper.ToolCallRequest mapped)
                throws Exception {
            return HandlerFutures.joinInterruptibly(handleAsync(context, mapped));
        }

        @Override
        public CompletionStage<Object> handleAsync(
                DispatchContext context, ProtocolRequestMapper.ToolCallRequest mapped) {
            var request = mapped.request();
            if (request.name().length() > DefaultToolRegistry.MAX_NAME_LENGTH) {
                return CompletableFuture.completedFuture(invalidParams("Tool name exceeds maximum length (SEP-986)"));
            }

            var handler = registry.get(request.name());
            if (handler == null) {
                return CompletableFuture.completedFuture(invalidParams("Unknown tool: " + request.name()));
            }
            var extensionId = handler.descriptor().extensionId();
            if (extensionId != null && !context.isExtensionEnabled(extensionId)) {
                return CompletableFuture.completedFuture(invalidParams("Unknown tool: " + request.name()));
            }

            var validationError = validateInput(handler.descriptor().inputSchema(), request);
            if (validationError != null) return CompletableFuture.completedFuture(invalidParams(validationError));

            var taskSupport = handler.descriptor().taskSupport();
            if (taskSupport == null) taskSupport = TaskSupport.FORBIDDEN;

            var taskDispatch = dispatchIfTaskAugmented(context, handler, request, mapped, taskSupport);
            if (taskDispatch != null) return CompletableFuture.completedFuture(taskDispatch);

            sendLogging(context, request.name(), "started");
            return HandlerFutures.invokeAndMap(
                    "Tool '" + request.name() + "' returned a null CompletionStage",
                    () -> handler.handleAsync(context, request),
                    context.engine().executor(),
                    (toolResult, cause) -> {
                        if (cause != null) return handlerError(request.name(), cause);
                        sendLogging(context, request.name(), "completed");
                        return context.responseMapper()
                                .callToolResult(
                                        prepareResult(handler.descriptor().outputSchema(), toolResult));
                    });
        }

        /**
         * Decides whether this call runs as a task instead of synchronously, dispatching it if so.
         * Returns {@code null} to signal "run synchronously" -- the only case that falls through to
         * {@link #handleAsync}'s normal dispatch below.
         */
        private @Nullable Object dispatchIfTaskAugmented(
                DispatchContext context,
                ToolHandler handler,
                ToolRequest request,
                ProtocolRequestMapper.ToolCallRequest mapped,
                TaskSupport taskSupport) {
            if (context.requestMapper().supportsLegacyTaskAugmentation()) {
                if (taskSupport == TaskSupport.FORBIDDEN && mapped.taskAugmented()) {
                    return invalidParams("Task augmentation not supported for this tool");
                }
                if (taskSupport == TaskSupport.REQUIRED && !mapped.taskAugmented()) {
                    return invalidParams("Task augmentation required for this tool");
                }
                return mapped.taskAugmented()
                        ? dispatchTaskAugmented(context, handler, request, mapped.taskTtl())
                        : null;
            }
            if (taskSupport != TaskSupport.REQUIRED) {
                return null;
            }
            // MCP 2026-07-28 (SEP-2663): task creation is server-directed, not client-requested --
            // the legacy "task" field is ignored entirely (see McpRequestMapper). A REQUIRED tool
            // can't run synchronously, so the server always creates a task for it, gated on the
            // client having declared the tasks extension per request (the server can't return a
            // CreateTaskResult otherwise).
            var missingCapability = TasksExtension.requireDeclared(context);
            return missingCapability != null
                    ? missingCapability
                    : dispatchTaskAugmented(context, handler, request, null);
        }

        private @Nullable Object dispatchTaskAugmented(
                DispatchContext context, ToolHandler handler, ToolRequest request, @Nullable Duration taskTtl) {
            sendLogging(context, request.name(), "started");
            var engine = context.engine();
            var taskRegistry = engine.tasksRegistry();
            var task = taskRegistry.createSessionTask(
                    taskTtl,
                    request.meta(),
                    OutboundSseStreamMessageRouter.currentSessionId(),
                    request.progressToken());
            task.transitionTo(TaskState.WORKING);
            var taskResult = context.responseMapper().createTaskResult(task);
            var taskRequest = ToolRequest.builder()
                    .name(request.name())
                    .arguments(request.arguments())
                    .meta(request.meta())
                    .progressToken(request.progressToken())
                    .payloadDeserializer(request.payloadDeserializer())
                    .inputResponses(request.inputResponses())
                    .requestState(request.requestState())
                    .task(task)
                    .build();

            taskRegistry.registerResumer(task.id(), (resumeContext, responses, state) -> {
                var resumedRequest = ToolRequest.builder()
                        .from(taskRequest)
                        .inputResponses(responses)
                        .requestState(state)
                        .build();
                dispatchAndWire(resumeContext, handler, resumedRequest, taskRegistry, task);
            });
            dispatchAndWire(context, handler, taskRequest, taskRegistry, task);
            return taskResult;
        }

        /**
         * Runs {@code taskRequest} on the executor and wires its result through {@link
         * #completeTask} -- shared by the first dispatch and every {@code tasks/update}-triggered
         * resume, so a second/third/Nth {@code InputRequired} round reuses the same
         * future/cancellation/completion plumbing as the initial call.
         */
        private void dispatchAndWire(
                DispatchContext context,
                ToolHandler handler,
                ToolRequest taskRequest,
                TaskRegistry taskRegistry,
                TaskEntry task) {
            var engine = context.engine();
            var future = new CompletableFuture<ToolResult>();
            var handlerFuture = new AtomicReference<@Nullable CompletableFuture<? extends ToolResult>>();
            var dispatchFuture = engine.executor().submit(() -> {
                if (future.isCancelled()) return;
                try {
                    var stage = Objects.requireNonNull(
                            handler.handleAsync(context, taskRequest),
                            "Tool '" + taskRequest.name() + "' returned a null CompletionStage");
                    var actualFuture = stage.toCompletableFuture();
                    handlerFuture.set(actualFuture);
                    if (future.isCancelled()) {
                        actualFuture.cancel(true);
                    } else {
                        actualFuture.whenComplete((result, failure) -> {
                            if (failure == null) future.complete(result);
                            else future.completeExceptionally(failure);
                        });
                    }
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                }
            });
            future.whenComplete((result, failure) -> {
                if (!future.isCancelled()) return;
                dispatchFuture.cancel(true);
                var actualFuture = handlerFuture.get();
                if (actualFuture != null) actualFuture.cancel(true);
            });

            taskRegistry.registerRunning(task.id(), future);
            HandlerFutures.completeOn(future, engine.executor(), (toolResult, failure) -> {
                        if (failure == null)
                            completeTask(
                                    taskRegistry, task, handler.descriptor().outputSchema(), toolResult);
                        return null;
                    })
                    .exceptionally(throwable -> {
                        handleFutureFailure(taskRegistry, task, throwable);
                        return null;
                    });
            future.exceptionally(throwable -> {
                handleFutureFailure(taskRegistry, task, throwable);
                return null;
            });
        }

        private void completeTask(
                TaskRegistry taskRegistry,
                TaskEntry task,
                @Nullable JsonSchema outputSchema,
                @Nullable ToolResult result) {
            if (result == null) {
                throw new NullPointerException("Tool handler completed task " + task.id() + " with a null result");
            }
            result = prepareResult(outputSchema, result);
            var meta = result.meta();
            switch (result) {
                case ToolResult.Error error -> {
                    task.fail(new TaskResult.Failed(error.content(), null, meta));
                    taskRegistry.unregisterResumer(task.id());
                }
                case ToolResult.Success success -> {
                    task.complete(new TaskResult.Completed(success.content(), success.structuredValue(), meta));
                    taskRegistry.unregisterResumer(task.id());
                }
                case ToolResult.InputRequired inputRequired ->
                    task.requireInput(inputRequired.request(), "Input required");
            }
            taskRegistry.unregisterRunning(task.id());
        }

        private ToolResult prepareResult(@Nullable JsonSchema outputSchema, ToolResult result) {
            var serialized = JsonUtils.serializeStructured(result, payloadSerializer);
            var validationError = validateOutput(outputSchema, serialized);
            if (validationError == null) return serialized;
            var error = ToolResult.error(validationError);
            var meta = serialized.meta();
            return meta == null || meta.isEmpty() ? error : error.withMeta(meta);
        }

        private @Nullable String validateInput(@Nullable JsonSchema schema, ToolRequest request) {
            if (schema == null) return null;
            var errors = inputValidator.validate(
                    schema, JsonDocument.of(request.arguments().json()));
            return errors.isEmpty() ? null : SchemaValidationError.join(errors);
        }

        private @Nullable String validateOutput(@Nullable JsonSchema schema, ToolResult result) {
            if (schema == null || outputValidator == JsonSchemaValidator.noop()) return null;
            if (!(result instanceof ToolResult.Success success) || success.structuredValue() == null) return null;
            var value = success.structuredValue();
            var json = value instanceof JsonDocument document ? document.json() : payloadSerializer.serialize(value);
            var errors = outputValidator.validate(schema, JsonDocument.of(json));
            return errors.isEmpty() ? null : SchemaValidationError.join(errors);
        }

        private Object handlerError(String name, Throwable cause) {
            if (cause instanceof CancellationException) {
                logger.debug("Tool call cancelled for '{}'", name);
                return internalError("Tool call cancelled");
            }
            var error = fromUnhandledException(cause, "Tool handler failed");
            if (error.kind() == ServerError.Kind.INVALID_PARAMS) {
                logger.debug("Tool handler rejected invalid params for '{}'", name);
            } else if (error.kind() == ServerError.Kind.INTERNAL_ERROR) {
                logger.error("Tool handler error for '{}'", name, cause);
            }
            return error;
        }

        private void handleFutureFailure(TaskRegistry taskRegistry, TaskEntry task, Throwable throwable) {
            var cause = HandlerFutures.unwrap(throwable);
            if (cause instanceof CancellationException) {
                logger.debug("Task handler cancelled for taskId={}", task.id());
            } else {
                handleTaskError(task, cause);
            }
            taskRegistry.unregisterRunning(task.id());
            taskRegistry.unregisterResumer(task.id());
        }

        private void handleTaskError(TaskEntry task, Throwable exception) {
            var cause = HandlerFutures.unwrap(exception);
            var error = fromUnhandledException(cause, "Tool handler failed");
            if (error.kind() == ServerError.Kind.INVALID_PARAMS) {
                logger.debug("Task handler rejected invalid params for taskId={}", task.id());
            } else if (error.kind() == ServerError.Kind.INTERNAL_ERROR) {
                logger.error("Task handler error for taskId={}", task.id(), cause);
            }
            task.fail(TaskResult.failed(error));
        }

        private void sendLogging(DispatchContext context, String toolName, String status) {
            context.notifications()
                    .log(LoggingLevel.DEBUG, "tachyon.tools", Map.of("tool", toolName, "status", status));
        }
    }
}
