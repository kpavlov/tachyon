/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tools;

import static dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors.internalError;
import static dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors.invalidParams;
import static dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors.invalidRequest;
import static dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors.methodNotFound;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.ProtocolCodecUtil;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CallToolRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskMetadata;
import dev.tachyonmcp.server.OutboundSseStreamMessageRouter;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.config.FeatureConfig;
import dev.tachyonmcp.server.config.Mode;
import dev.tachyonmcp.server.features.ChangeSupport;
import dev.tachyonmcp.server.features.HandlerFutures;
import dev.tachyonmcp.server.features.ListRequests;
import dev.tachyonmcp.server.features.PaginatedResult;
import dev.tachyonmcp.server.features.Pagination;
import dev.tachyonmcp.server.features.tasks.TaskDescriptor;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tasks.TaskState;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.json.JsonSchemaUtils;
import dev.tachyonmcp.server.json.JsonSchemaValidator;
import dev.tachyonmcp.server.json.JsonUtils;
import dev.tachyonmcp.server.json.PayloadDeserializer;
import dev.tachyonmcp.server.json.PayloadSerializer;
import dev.tachyonmcp.server.json.SchemaValidationError;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Registry for tool handlers with input/output schema validation.
 */
@InternalApi
public class ToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);

    private final ConcurrentHashMap<String, ToolHandler> handlers = new ConcurrentHashMap<>();
    private final JsonSchemaValidator inputValidator;
    private final JsonSchemaValidator outputValidator;
    private final PayloadSerializer payloadSerializer;
    private final PayloadDeserializer payloadDeserializer;
    private final FeatureConfig config;

    private final ChangeSupport changes = new ChangeSupport();

    /**
     * Maximum description length before a warning is logged. MCP clients may truncate
     * descriptions beyond this length.
     */
    public static final int MAX_DESCRIPTION_LENGTH = 2048;

    /**
     * Creates a tool registry with the given schema validators and payload serde.
     */
    public ToolRegistry(
            JsonSchemaValidator inputValidator,
            JsonSchemaValidator outputValidator,
            PayloadSerializer payloadSerializer,
            PayloadDeserializer payloadDeserializer,
            FeatureConfig config) {
        this.inputValidator = inputValidator;
        this.outputValidator = outputValidator;
        this.payloadSerializer = payloadSerializer;
        this.payloadDeserializer = payloadDeserializer;
        this.config = config;
    }

    public void onChange(Runnable callback) {
        changes.onChange(callback);
    }

    private void fireOnChange() {
        changes.fireOnChange();
    }

    /**
     * Registers a tool handler.
     */
    public void register(ToolHandler handler) {
        Objects.requireNonNull(handler, "ToolHandler must not be null");
        var descriptor = handler.descriptor();
        Objects.requireNonNull(descriptor, "ToolDescriptor must not be null");
        if (config.mode() == Mode.OFF) {
            logger.debug("Tool '{}' not registered: tools capability is OFF", descriptor.name());
            return;
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
        handlers.put(name, handler);
        fireOnChange();
    }

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
     * Removes the tool with the given name.
     */
    public void remove(String name) {
        if (handlers.remove(name) != null) {
            fireOnChange();
        }
    }

    /**
     * Returns the tool handler by name.
     */
    public @Nullable ToolHandler get(String name) {
        return handlers.get(name);
    }

    /**
     * Returns the tool descriptor by name.
     */
    public @Nullable ToolDescriptor getDescriptor(String name) {
        var handler = handlers.get(name);
        return handler != null ? handler.descriptor() : null;
    }

    /**
     * Returns all registered tool handlers.
     */
    public Collection<ToolHandler> getAll() {
        return handlers.values();
    }

    /**
     * Returns whether the registry is empty.
     */
    public boolean isEmpty() {
        return handlers.isEmpty();
    }

    public PaginatedResult<ToolDescriptor> list(int limit, @Nullable String cursor) {
        int lim = limit > 0 ? limit : config.pageSize();
        var all = handlers.values().stream()
                .map(ToolHandler::descriptor)
                .sorted(Comparator.comparing(ToolDescriptor::name))
                .toList();
        return Pagination.paginate(all, lim, cursor, ToolDescriptor::name);
    }

    public PaginatedResult<ToolDescriptor> list(int limit, @Nullable String cursor, Predicate<ToolDescriptor> filter) {
        int lim = limit > 0 ? limit : config.pageSize();
        var all = handlers.values().stream()
                .map(ToolHandler::descriptor)
                .filter(filter)
                .sorted(Comparator.comparing(ToolDescriptor::name))
                .toList();
        return Pagination.paginate(all, lim, cursor, ToolDescriptor::name);
    }

    public void registerHandlers(Map<String, RpcMethodHandler> registry) {
        registry.put("tools/list", new ToolsListHandler(this));
        registry.put(
                "tools/call",
                new ToolsCallHandler(this, inputValidator, outputValidator, payloadSerializer, payloadDeserializer));
    }

    private record ToolsListHandler(ToolRegistry registry) implements RpcMethodHandler {

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
            return context.responseMapper().listToolsResult(paginated.items(), paginated.nextCursor());
        }
    }

    private record ToolsCallHandler(
            ToolRegistry registry,
            JsonSchemaValidator inputValidator,
            JsonSchemaValidator outputValidator,
            PayloadSerializer payloadSerializer,
            PayloadDeserializer payloadDeserializer)
            implements RpcMethodHandler {

        @Override
        public String method() {
            return "tools/call";
        }

        /**
         * Runs on the dispatcher's virtual thread; blocking to join the handler is the SPI contract.
         */
        @Override
        public Object handle(DispatchContext context, Object params) {
            var parsed = parseCallParams(params);
            if (parsed == null) return invalidRequest("Invalid params");
            if (parsed.name().isBlank()) return invalidRequest("Missing tool name");
            if (parsed.name().length() > 64) return invalidRequest("Tool name exceeds maximum length (SEP-986)");

            var handler = registry.get(parsed.name());
            if (handler == null) return methodNotFound("Method not found");
            var extId = handler.descriptor().extensionId();
            if (extId != null && !context.isExtensionEnabled(extId)) return methodNotFound("Method not found");

            var validationError = validateInput(handler.descriptor().inputSchema(), parsed.args());
            if (validationError != null) return invalidParams(validationError);

            var taskSupport = handler.descriptor().taskSupport();
            if (taskSupport == null) taskSupport = TaskSupport.FORBIDDEN;
            var taskMeta = parsed.task();

            if (taskSupport == TaskSupport.FORBIDDEN && taskMeta != null) {
                return invalidRequest("Task augmentation not supported for this tool");
            }
            if (taskSupport == TaskSupport.REQUIRED && taskMeta == null) {
                return invalidRequest("Task augmentation required for this tool");
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
                return dispatchTaskAugmented(context, handler, request, parsed.name(), taskMeta);
            }

            // Non-task path: run the handler on this virtual thread, blocking as the SPI allows.
            sendLoggingIfEnabled(context, parsed.name(), "started");
            ToolResult toolResult;
            try {
                toolResult = HandlerFutures.joinInterruptibly(handler.handleAsync(context, request));
            } catch (InvalidArgumentException e) {
                return invalidParams("invalid argument '" + e.argName() + "': " + e.getMessage());
            } catch (Exception e) {
                logger.error("Tool handler error for '{}'", parsed.name(), e);
                return internalError("Tool handler failed");
            }
            sendLoggingIfEnabled(context, parsed.name(), "completed");
            validateOutput(handler.descriptor().outputSchema(), toolResult);
            return context.responseMapper()
                    .callToolResult(JsonUtils.serializeStructured(toolResult, payloadSerializer));
        }

        private Object dispatchTaskAugmented(
                DispatchContext context, ToolHandler handler, ToolRequest request, String name, TaskMetadata taskMeta) {
            sendLoggingIfEnabled(context, name, "started");
            var ttl = taskMeta.ttl() != null ? taskMeta.ttl() / 1000.0 : 0.0;
            var engine = context.engine();
            var tasks = engine.tasks();
            var sessionId = OutboundSseStreamMessageRouter.currentSessionId();
            var outboundStream = OutboundSseStreamMessageRouter.currentOutboundSseStream();
            var descriptor = TaskDescriptor.builder(name)
                    .description(handler.descriptor().description())
                    .build();
            var taskEntry = tasks.createTask(descriptor, ttl, sessionId);
            var taskFuture = engine.executor().submit(() -> {
                try {
                    OutboundSseStreamMessageRouter.withDispatchContext(sessionId, outboundStream, () -> {
                        var toolResult = HandlerFutures.joinInterruptibly(handler.handleAsync(context, request));
                        sendLoggingIfEnabled(context, name, "completed");
                        validateOutput(handler.descriptor().outputSchema(), toolResult);
                        var resultJson = JsonRpcCodec.writeValueAsString(context.responseMapper()
                                .callToolResult(JsonUtils.serializeStructured(toolResult, payloadSerializer)));
                        tasks.completeTask(taskEntry.id(), resultJson);
                        return null;
                    });
                } catch (Exception e) {
                    handleTaskError(context, taskEntry, e);
                }
                return null;
            });
            tasks.registerRunning(taskEntry.id(), taskFuture);
            // The task may reach a terminal state before registerRunning: either the handler
            // finished, or a tasks/cancel arrived in the window (which flips state but finds no
            // future to interrupt). Drop the now-untracked future, and if the terminal state was
            // a cancellation, interrupt the still-running handler ourselves.
            if (!taskEntry.status().isActive()) {
                tasks.unregisterRunning(taskEntry.id());
                if (taskEntry.status() == TaskState.CANCELLED) {
                    taskFuture.cancel(true);
                }
            }
            return context.responseMapper().createTaskResult(taskEntry);
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
            if (params instanceof CallToolRequestParams p) {
                var name = p.name();
                if (name == null) return null;
                return new CallParams(name, p.arguments(), p._meta(), p.inputResponses(), p.requestState(), p.task());
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

        private void handleTaskError(DispatchContext context, TaskEntry taskEntry, Exception e) {
            var cause = e instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : e;
            logger.error("Task handler error for '{}'", taskEntry.name(), cause);
            try {
                var errorJson = JsonRpcCodec.writeValueAsString(Map.of("error", "Internal server error"));
                context.engine().tasks().failTask(taskEntry.id(), errorJson);
            } catch (Exception ex) {
                logger.error("Failed to record task failure for {}", taskEntry.id(), ex);
            }
        }

        private void sendLoggingIfEnabled(DispatchContext context, String toolName, String status) {
            var session = context.session();
            if (session == null) return;
            var level = context.getLoggingLevel();
            if (level == null) return;
            context.engine().log(session, level, "tachyon.tools", Map.of("tool", toolName, "status", status));
        }
    }
}
