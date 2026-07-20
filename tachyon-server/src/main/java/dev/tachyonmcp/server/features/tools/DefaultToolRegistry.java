/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tools;

import static dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors.internalError;
import static dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors.invalidParams;
import static dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors.invalidRequest;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.ProtocolCodecUtil;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CallToolRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskMetadata;
import dev.tachyonmcp.server.OutboundSseStreamMessageRouter;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.config.FeatureConfig;
import dev.tachyonmcp.server.config.Mode;
import dev.tachyonmcp.server.domain.InvalidArgumentException;
import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.domain.TaskResult;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.features.AbstractRegistry;
import dev.tachyonmcp.server.features.HandlerFutures;
import dev.tachyonmcp.server.features.ListRequests;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.json.JsonSchemaUtils;
import dev.tachyonmcp.server.json.JsonSchemaValidator;
import dev.tachyonmcp.server.json.JsonUtils;
import dev.tachyonmcp.server.json.PayloadDeserializer;
import dev.tachyonmcp.server.json.PayloadSerializer;
import dev.tachyonmcp.server.json.SchemaValidationError;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * AbstractRegistry for tool handlers with input/output schema validation.
 */
@InternalApi
public class DefaultToolRegistry extends AbstractRegistry<ToolDescriptor, ToolHandler> implements ToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DefaultToolRegistry.class);

    private final JsonSchemaValidator inputValidator;
    private final JsonSchemaValidator outputValidator;
    private final PayloadSerializer payloadSerializer;
    private final PayloadDeserializer payloadDeserializer;
    private final FeatureConfig config;

    /**
     * Maximum description length before a warning is logged. MCP clients may truncate
     * descriptions beyond this length.
     */
    public static final int MAX_DESCRIPTION_LENGTH = 2048;

    /**
     * Creates a tool registry with the given schema validators and payload serde.
     */
    public DefaultToolRegistry(
            JsonSchemaValidator inputValidator,
            JsonSchemaValidator outputValidator,
            PayloadSerializer payloadSerializer,
            PayloadDeserializer payloadDeserializer,
            FeatureConfig config) {
        super(config.pageSize());
        this.inputValidator = inputValidator;
        this.outputValidator = outputValidator;
        this.payloadSerializer = payloadSerializer;
        this.payloadDeserializer = payloadDeserializer;
        this.config = config;
    }

    /**
     * Registers a tool handler when the tools capability is enabled.
     *
     * @param handler the tool handler to register
     * @return this registry
     * @throws NullPointerException if the handler or its descriptor is null
     * @throws IllegalArgumentException if the tool name or schema root is invalid
     */
    @Override
    public Tools register(ToolHandler handler) {
        Objects.requireNonNull(handler, "ToolHandler must not be null");
        var descriptor = handler.descriptor();
        Objects.requireNonNull(descriptor, "ToolDescriptor must not be null");
        if (config.mode() == Mode.OFF) {
            logger.debug("Tool '{}' not registered: tools capability is OFF", descriptor.name());
            return this;
        }
        var name = descriptor.name();
        validateName(name);
        validateSchemaRoot("inputSchema", name, descriptor.inputSchema());
        validateSchemaRoot("outputSchema", name, descriptor.outputSchema());
        var desc = descriptor.description();
        if (desc != null && desc.length() > MAX_DESCRIPTION_LENGTH) {
            logger.warn(
                    "Tool '{}' description exceeds {} characters ({}), may be truncated by clients",
                    name,
                    MAX_DESCRIPTION_LENGTH,
                    desc.length());
        }
        addItem(handler);
        logger.debug("Tool registered: {}", name);
        return this;
    }

    /**
     * Removes the registered tool with the specified name.
     *
     * @param name the name of the tool to remove
     * @return {@code true} if a tool was removed, {@code false} otherwise
     */
    @Override
    public boolean unregister(String name) {
        return removeItem(name);
    }

    /**
     * Finds the descriptor for a registered tool by name.
     *
     * @param name the tool name to find
     * @return the tool descriptor if registered, or an empty optional otherwise
     */
    @Override
    public Optional<ToolDescriptor> find(String name) {
        var handler = get(name);
        return handler != null ? Optional.of(handler.descriptor()) : Optional.empty();
    }

    /**
     * Retrieves all registered tool descriptors in name order.
     *
     * @return the registered tool descriptors sorted by name
     */
    @Override
    public List<ToolDescriptor> descriptors() {
        return getAll().stream()
                .map(ToolHandler::descriptor)
                .sorted(Comparator.comparing(ToolDescriptor::name))
                .toList();
    }

    /**
     * Validates that a tool schema has an object root declaring {@code "type": "object"}.
     *
     * @param schemaKind the kind of schema being validated
     * @param toolName   the name of the tool owning the schema
     * @param schema     the schema to validate, or {@code null}
     * @throws IllegalArgumentException if the schema root is invalid
     */
    private static void validateSchemaRoot(String schemaKind, String toolName, @Nullable JsonNode schema) {
        if (schema == null) return;
        final String detail;
        if (!schema.isObject()) {
            detail = "got: " + schema.getNodeType();
        } else if (!schema.has("type")) {
            detail = "missing \"type\"";
        } else if (!"object".equals(schema.get("type").asString())) {
            detail = "got: " + schema.get("type");
        } else {
            return;
        }
        throw new IllegalArgumentException(
                "Tool '" + toolName + "' " + schemaKind + " root must declare \"type\": \"object\", " + detail);
    }

    static void validateName(String name) {
        Objects.requireNonNull(name, "Tool name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
        if (name.length() > 64) {
            throw new IllegalArgumentException("Tool name must not exceed 64 characters (SEP-986)");
        }
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Tool name must match [a-zA-Z0-9_\\-./]+ per SEP-986: " + name);
        }
    }

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-./]+");

    /**
     * Registers the JSON-RPC handlers for listing and invoking tools.
     *
     * @param registry the registry to receive the tool method handlers
     */
    public void registerHandlers(Map<String, RpcMethodHandler> registry) {
        registry.put("tools/list", new ToolsListHandler(this));
        registry.put(
                "tools/call",
                new ToolsCallHandler(this, inputValidator, outputValidator, payloadSerializer, payloadDeserializer));
    }

    private record ToolsListHandler(DefaultToolRegistry registry) implements RpcMethodHandler {

        @Override
        public String method() {
            return "tools/list";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var limit = ListRequests.parseLimit(params);
            var cursor = ListRequests.parseCursor(params);
            var paginated = registry.list(limit, cursor, d -> {
                var extId = d.extensionId();
                return extId == null || context.isExtensionEnabled(extId);
            });
            if (!paginated.cursorValid()) {
                return invalidParams("Invalid cursor");
            }
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

        @Override
        public String method() {
            return "tools/call";
        }

        /** Compatibility fallback for callers invoking the blocking SPI method directly. */
        @Override
        public Object handle(DispatchContext context, Object params) throws Exception {
            return HandlerFutures.joinInterruptibly(handleAsync(context, params));
        }

        /** Runs on the dispatcher's virtual thread; composes the handler's stage without blocking it. */
        @Override
        public CompletionStage<Object> handleAsync(DispatchContext context, Object params) {
            var parsed = parseCallParams(params);
            if (parsed == null) return CompletableFuture.completedFuture(invalidRequest("Invalid params"));
            if (parsed.name().isBlank()) {
                return CompletableFuture.completedFuture(invalidRequest("Missing tool name"));
            }
            if (parsed.name().length() > 64) {
                return CompletableFuture.completedFuture(invalidRequest("Tool name exceeds maximum length (SEP-986)"));
            }

            var handler = registry.get(parsed.name());
            if (handler == null) {
                return CompletableFuture.completedFuture(invalidParams("Unknown tool: " + parsed.name()));
            }
            var extId = handler.descriptor().extensionId();
            if (extId != null && !context.isExtensionEnabled(extId)) {
                return CompletableFuture.completedFuture(invalidParams("Unknown tool: " + parsed.name()));
            }

            var validationError = validateInput(handler.descriptor().inputSchema(), parsed.args());
            if (validationError != null) return CompletableFuture.completedFuture(invalidParams(validationError));

            var taskSupport = handler.descriptor().taskSupport();
            if (taskSupport == null) taskSupport = TaskSupport.FORBIDDEN;
            var taskMeta = parsed.task();

            if (taskSupport == TaskSupport.FORBIDDEN && taskMeta != null) {
                return CompletableFuture.completedFuture(
                        invalidRequest("Task augmentation not supported for this tool"));
            }
            if (taskSupport == TaskSupport.REQUIRED && taskMeta == null) {
                return CompletableFuture.completedFuture(invalidRequest("Task augmentation required for this tool"));
            }

            var request = ToolRequest.builder()
                    .name(parsed.name())
                    .arguments(parsed.args() != null ? parsed.args() : Collections.emptyMap())
                    .meta(parsed.meta())
                    .progressToken(parseProgressToken(parsed.meta()))
                    .payloadDeserializer(payloadDeserializer)
                    .inputResponses(parsed.inputResponses())
                    .requestState(parsed.requestState())
                    .build();

            // Task-augmented path: branch BEFORE invoking handler so the dispatch thread
            // returns CreateTaskResult immediately (even for sync/suspend handlers).
            if (taskMeta != null) {
                return CompletableFuture.completedFuture(
                        dispatchTaskAugmented(context, handler, request, parsed.name(), taskMeta));
            }

            sendLoggingIfEnabled(context, parsed.name(), "started");
            // invokeAndMap: guards the synchronous-throw/null-stage cases, then re-anchors onto a
            // tachyon- virtual thread only when the handler's stage is still pending, so a
            // foreign completer thread never leaks into output validation/response mapping,
            // without adding an executor hop to the common already-resolved case.
            return HandlerFutures.invokeAndMap(
                    "Tool '" + parsed.name() + "' returned a null CompletionStage",
                    () -> handler.handleAsync(context, request),
                    context.engine().executor(),
                    (toolResult, ex) -> {
                        if (ex != null) {
                            var cause = HandlerFutures.unwrap(ex);
                            if (cause instanceof InvalidArgumentException e) {
                                return invalidParams("invalid argument '" + e.argName() + "': " + e.getMessage());
                            }
                            if (cause instanceof CancellationException) {
                                logger.debug("Tool call cancelled for '{}'", parsed.name());
                                return internalError("Tool call cancelled");
                            }
                            logger.error("Tool handler error for '{}'", parsed.name(), cause);
                            return internalError("Tool handler failed");
                        }
                        sendLoggingIfEnabled(context, parsed.name(), "completed");
                        validateOutput(handler.descriptor().outputSchema(), toolResult);
                        return context.responseMapper()
                                .callToolResult(JsonUtils.serializeStructured(toolResult, payloadSerializer));
                    });
        }

        /**
         * Dispatches a tool request as a session task and returns its initial task result.
         *
         * @param context  the dispatch context used to execute the tool and create the response
         * @param handler  the tool handler to invoke
         * @param request  the tool request to execute
         * @param id       the tool identifier used for logging
         * @param taskMeta task execution metadata, including the optional time-to-live
         * @return         the initial result representing the created task
         */
        private Object dispatchTaskAugmented(
                DispatchContext context, ToolHandler handler, ToolRequest request, String id, TaskMetadata taskMeta) {
            sendLoggingIfEnabled(context, id, "started");
            var engine = context.engine();
            var taskRegistry = engine.tasksRegistry();
            var sessionId = OutboundSseStreamMessageRouter.currentSessionId();
            var ttl = taskMeta.ttl() != null ? Duration.ofMillis(taskMeta.ttl()) : null;
            var progressToken = parseProgressToken(request.meta());
            var task = taskRegistry.createSessionTask(ttl, request.meta(), sessionId, progressToken);

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

            // Hop onto engine.executor() so a blocking sync handler doesn't delay returning
            // CreateTaskResult; thenCompose flattens the stage without blocking either thread.
            var future = CompletableFuture.supplyAsync(
                            () -> handler.handleAsync(context, taskRequest), engine.executor())
                    .thenCompose(Function.identity());

            taskRegistry.registerRunning(task.id(), future);

            future.thenAccept(toolResult -> {
                if (toolResult == null) {
                    return;
                }
                if (toolResult instanceof ToolResult.Deferred) {
                    return;
                }
                if (toolResult instanceof ToolResult.Error(String message)) {
                    task.fail(new TaskResult.Failed(List.of(TextContent.of(message)), null, null));
                } else if (toolResult
                        instanceof
                        ToolResult.Success(
                                Object structuredValue,
                                List<dev.tachyonmcp.server.domain.ContentBlock> content)) {
                    var structured = JsonUtils.valueToObjectNode(structuredValue, payloadSerializer);
                    task.complete(new TaskResult.Completed(content, structured, null));
                }
                taskRegistry.unregisterRunning(task.id());
            });
            future.exceptionally(throwable -> {
                var e = throwable instanceof CompletionException ce && ce.getCause() != null
                        ? ce.getCause()
                        : throwable;
                if (e instanceof Exception ex) {
                    handleTaskError(task, ex);
                } else {
                    logger.error("Non-exception throwable in task handler for '{}'", task.name(), e);
                }
                taskRegistry.unregisterRunning(task.id());
                return null;
            });

            return context.responseMapper().createTaskResult(task);
        }

        private static @Nullable Object parseProgressToken(@Nullable Map<String, JsonNode> meta) {
            if (meta == null) return null;
            var ptNode = meta.get("progressToken");
            if (ptNode == null) return null;
            if (ptNode.isIntegralNumber()) return ptNode.asLong();
            return ptNode.asString();
        }

        private record CallParams(
                String name,
                @Nullable Map<String, JsonNode> args,
                @Nullable Map<String, JsonNode> meta,
                @Nullable Map<String, JsonNode> inputResponses,
                @Nullable String requestState,
                @Nullable TaskMetadata task) {}

        private @Nullable CallParams parseCallParams(Object params) {
            if (params instanceof Map<?, ?> map) {
                var json = JsonRpcCodec.writeValueAsString(map);
                var typed = ProtocolCodecUtil.decodeWithCodec(json, CallToolRequestParams.class);
                var name = typed.name();
                if (name == null) return null;
                var inputResponses = ListRequests.extractInputResponses(map.get("inputResponses"));
                var requestState = map.get("requestState") instanceof String s ? s : null;
                return new CallParams(
                        name, typed.arguments(), typed._meta(), inputResponses, requestState, typed.task());
            }
            if (params
                    instanceof
                    CallToolRequestParams(
                            String name,
                            Map<String, JsonNode> arguments,
                            Map<String, JsonNode> inputResponses,
                            String requestState,
                            Map<String, JsonNode> meta,
                            TaskMetadata task)) {
                if (name == null) return null;
                return new CallParams(name, arguments, meta, inputResponses, requestState, task);
            }
            return null;
        }

        private @Nullable String validateInput(@Nullable JsonNode schema, @Nullable Map<String, JsonNode> args) {
            return JsonSchemaUtils.validateArguments(inputValidator, schema, args);
        }

        private void validateOutput(@Nullable JsonNode schema, ToolResult result) {
            if (schema == null || outputValidator == JsonSchemaValidator.NOOP) return;
            var inner = result instanceof ToolResult.WithMeta wm ? wm.inner() : result;
            if (!(inner instanceof ToolResult.Success s)) return;
            var contentNode = JsonUtils.valueToObjectNode(s.structuredValue(), payloadSerializer);
            if (contentNode == null) return;
            var errors = outputValidator.validate(schema, contentNode);
            if (!errors.isEmpty()) {
                logger.debug(
                        "Tool output failed schema validation (advisory only): {}", SchemaValidationError.join(errors));
            }
        }

        private void handleTaskError(TaskEntry taskEntry, Exception e) {
            var cause = e instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : e;
            logger.error("Task handler error for '{}'", taskEntry.name(), cause);
            taskEntry.fail(new TaskResult.Failed(List.of(TextContent.of("Internal server error")), null, null));
        }

        private void sendLoggingIfEnabled(DispatchContext context, String toolName, String status) {
            context.notifications()
                    .log(LoggingLevel.DEBUG, "tachyon.tools", Map.of("tool", toolName, "status", status));
        }
    }
}
