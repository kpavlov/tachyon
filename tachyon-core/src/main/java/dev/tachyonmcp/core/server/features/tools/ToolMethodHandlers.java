/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tools;

import static dev.tachyonmcp.core.server.domain.ServerErrors.fromUnhandledException;
import static dev.tachyonmcp.core.server.domain.ServerErrors.internalError;
import static dev.tachyonmcp.core.server.domain.ServerErrors.invalidParams;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.json.JsonSchemaValidator;
import dev.tachyonmcp.api.json.PayloadDeserializer;
import dev.tachyonmcp.api.json.PayloadSerializer;
import dev.tachyonmcp.api.json.SchemaValidationError;
import dev.tachyonmcp.api.server.domain.ContentBlock;
import dev.tachyonmcp.api.server.domain.LoggingLevel;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.features.HandlerFutures;
import dev.tachyonmcp.api.server.features.tasks.TaskSupport;
import dev.tachyonmcp.api.server.features.tools.ToolHandler;
import dev.tachyonmcp.api.server.features.tools.ToolRequest;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.server.OutboundSseStreamMessageRouter;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.features.tasks.TaskEntry;
import dev.tachyonmcp.core.server.features.tasks.TaskRegistry;
import dev.tachyonmcp.core.server.json.JsonUtils;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.time.Duration;
import java.util.List;
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
public final class ToolMethodHandlers {

    private ToolMethodHandlers() {}

    public static void register(
            Map<String, RpcMethodHandler> handlers,
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

    private record ToolsListHandler(DefaultToolRegistry registry) implements RpcMethodHandler {

        @Override
        public String method() {
            return "tools/list";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var page = context.requestMapper().page(params);
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
            implements RpcMethodHandler {

        private static final Logger logger = LoggerFactory.getLogger(ToolsCallHandler.class);

        @Override
        public String method() {
            return "tools/call";
        }

        @Override
        public Object handle(DispatchContext context, Object params) throws Exception {
            return HandlerFutures.joinInterruptibly(handleAsync(context, params));
        }

        @Override
        public CompletionStage<Object> handleAsync(DispatchContext context, Object params) {
            final dev.tachyonmcp.core.protocol.ProtocolRequestMapper.ToolCallRequest mapped;
            try {
                mapped = context.requestMapper().callTool(params, payloadDeserializer);
            } catch (RequestMappingException e) {
                return CompletableFuture.completedFuture(e.error());
            }
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
            if (taskSupport == TaskSupport.FORBIDDEN && mapped.taskAugmented()) {
                return CompletableFuture.completedFuture(
                        invalidParams("Task augmentation not supported for this tool"));
            }
            if (taskSupport == TaskSupport.REQUIRED && !mapped.taskAugmented()) {
                return CompletableFuture.completedFuture(invalidParams("Task augmentation required for this tool"));
            }
            if (mapped.taskAugmented()) {
                return CompletableFuture.completedFuture(
                        dispatchTaskAugmented(context, handler, request, mapped.taskTtl()));
            }

            sendLogging(context, request.name(), "started");
            return HandlerFutures.invokeAndMap(
                    "Tool '" + request.name() + "' returned a null CompletionStage",
                    () -> handler.handleAsync(context, request),
                    context.engine().executor(),
                    (toolResult, cause) -> {
                        if (cause != null) return handlerError(request.name(), cause);
                        sendLogging(context, request.name(), "completed");
                        validateOutput(handler.descriptor().outputSchema(), toolResult);
                        return context.responseMapper()
                                .callToolResult(JsonUtils.serializeStructured(toolResult, payloadSerializer));
                    });
        }

        private Object dispatchTaskAugmented(
                DispatchContext context, ToolHandler handler, ToolRequest request, @Nullable Duration taskTtl) {
            sendLogging(context, request.name(), "started");
            var taskRegistry = context.engine().tasksRegistry();
            var task = taskRegistry.createSessionTask(
                    taskTtl,
                    request.meta(),
                    OutboundSseStreamMessageRouter.currentSessionId(),
                    request.progressToken());
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

            var future = new CompletableFuture<ToolResult>();
            var handlerFuture = new AtomicReference<CompletableFuture<? extends ToolResult>>();
            var dispatchFuture = context.engine().executor().submit(() -> {
                if (future.isCancelled()) return;
                try {
                    var stage = Objects.requireNonNull(
                            handler.handleAsync(context, taskRequest),
                            "Tool '" + request.name() + "' returned a null CompletionStage");
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
            future.thenAccept(toolResult -> completeTask(taskRegistry, task, toolResult));
            future.exceptionally(throwable -> {
                var cause = HandlerFutures.unwrap(throwable);
                if (cause instanceof CancellationException) {
                    logger.debug("Task handler cancelled for taskId={}", task.id());
                } else if (cause instanceof Exception exception) {
                    handleTaskError(task, exception);
                } else {
                    logger.error("Non-exception throwable in task handler for taskId={}", task.id(), cause);
                }
                taskRegistry.unregisterRunning(task.id());
                return null;
            });
            return context.responseMapper().createTaskResult(task);
        }

        private void completeTask(TaskRegistry taskRegistry, TaskEntry task, @Nullable ToolResult result) {
            if (result == null || result instanceof ToolResult.Deferred) return;
            result = JsonUtils.serializeStructured(result, payloadSerializer);
            Map<String, Object> meta = null;
            if (result instanceof ToolResult.WithMeta(ToolResult inner, Map<String, Object> resultMeta)) {
                result = inner;
                meta = resultMeta;
            }
            if (result instanceof ToolResult.Error(String message)) {
                task.fail(new TaskResult.Failed(List.of(TextContent.of(message)), null, meta));
            } else if (result instanceof ToolResult.Success(Object structuredValue, List<ContentBlock> content)) {
                task.complete(new TaskResult.Completed(content, structuredValue, meta));
            }
            taskRegistry.unregisterRunning(task.id());
        }

        private @Nullable String validateInput(@Nullable JsonSchema schema, ToolRequest request) {
            if (schema == null) return null;
            var errors = inputValidator.validate(
                    schema, JsonDocument.of(request.arguments().json()));
            return errors.isEmpty() ? null : SchemaValidationError.join(errors);
        }

        private void validateOutput(@Nullable JsonSchema schema, ToolResult result) {
            if (schema == null || outputValidator == JsonSchemaValidator.noop()) return;
            var inner = result instanceof ToolResult.WithMeta withMeta ? withMeta.inner() : result;
            if (!(inner instanceof ToolResult.Success success) || success.structuredValue() == null) return;
            var value = success.structuredValue();
            var json = value instanceof JsonDocument document ? document.json() : payloadSerializer.serialize(value);
            var errors = outputValidator.validate(schema, JsonDocument.of(json));
            if (!errors.isEmpty()) {
                logger.debug(
                        "Tool output failed schema validation (advisory only): {}", SchemaValidationError.join(errors));
            }
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

        private void handleTaskError(TaskEntry task, Exception exception) {
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
