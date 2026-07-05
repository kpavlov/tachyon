/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import static dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors.invalidRequest;
import static dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors.methodNotFound;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.ProtocolCodecUtil;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CallToolRequestParams;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.features.PaginatedResult;
import dev.tachyonmcp.server.json.*;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Registry for tool handlers with input/output schema validation.
 */
public class ToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);

    private final ConcurrentHashMap<String, ToolHandler> handlers = new ConcurrentHashMap<>();
    private final JsonSchemaValidator inputValidator;
    private final JsonSchemaValidator outputValidator;
    private final PayloadSerializer payloadSerializer;
    private final PayloadDeserializer payloadDeserializer;

    private @Nullable Runnable onChange;

    private static final int DEFAULT_PAGE_SIZE = 50;

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
            PayloadDeserializer payloadDeserializer) {
        this.inputValidator = inputValidator;
        this.outputValidator = outputValidator;
        this.payloadSerializer = payloadSerializer;
        this.payloadDeserializer = payloadDeserializer;
    }

    public void onChange(@Nullable Runnable callback) {
        this.onChange = callback;
    }

    private void fireOnChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    /**
     * Registers a tool handler.
     */
    public void register(ToolHandler handler) {
        Objects.requireNonNull(handler, "ToolHandler must not be null");
        var descriptor = handler.descriptor();
        Objects.requireNonNull(descriptor, "ToolDescriptor must not be null");
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
        return list(limit, cursor, DEFAULT_PAGE_SIZE, descriptor -> true);
    }

    public PaginatedResult<ToolDescriptor> list(int limit, @Nullable String cursor, Predicate<ToolDescriptor> filter) {
        return list(limit, cursor, DEFAULT_PAGE_SIZE, filter);
    }

    public PaginatedResult<ToolDescriptor> list(int limit, @Nullable String cursor, int defaultLimit) {
        return list(limit, cursor, defaultLimit, descriptor -> true);
    }

    public PaginatedResult<ToolDescriptor> list(
            int limit, @Nullable String cursor, int defaultLimit, Predicate<ToolDescriptor> filter) {
        if (limit <= 0) {
            limit = defaultLimit;
        }
        var all = handlers.values().stream()
                .map(ToolHandler::descriptor)
                .filter(filter)
                .sorted(Comparator.comparing(ToolDescriptor::name))
                .toList();
        var result = new ArrayList<ToolDescriptor>();
        boolean pastCursor = cursor == null;
        for (var item : all) {
            if (!pastCursor) {
                if (item.name().equals(cursor)) {
                    pastCursor = true;
                }
                continue;
            }
            result.add(item);
            if (result.size() >= limit) break;
        }
        String nextCursor = null;
        if (result.size() >= limit) {
            var lastItem = result.getLast();
            int lastIdx = all.indexOf(lastItem);
            if (lastIdx >= 0 && lastIdx < all.size() - 1) {
                nextCursor = lastItem.name();
            }
        }
        return PaginatedResult.of(result, nextCursor);
    }

    public void registerHandlers(Map<String, RpcMethodHandler> registry) {
        registry.put("tools/list", new ToolsListHandler(this));
        registry.put(
                "tools/call",
                new ToolsCallHandler(this, inputValidator, outputValidator, payloadSerializer, payloadDeserializer));
    }

    public static int parseLimit(Object params) {
        if (params instanceof Map<?, ?> map && map.get("limit") instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    public static @Nullable String parseCursor(Object params) {
        if (params instanceof Map<?, ?> map && map.get("cursor") instanceof String s) {
            return s;
        }
        return null;
    }

    private record ToolsListHandler(ToolRegistry registry) implements RpcMethodHandler {

        @Override
        public String method() {
            return "tools/list";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            int limit = 0;
            String cursor = null;
            if (params instanceof Map<?, ?> map) {
                if (map.get("limit") instanceof Number n) limit = n.intValue();
                if (map.get("cursor") instanceof String s) cursor = s;
            }
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
         * Dispatch uses handleAsync; interruptible get on VT is safe.
         */
        @Override
        public Object handle(DispatchContext context, Object params) {
            try {
                return handleAsync(context, params).toCompletableFuture().get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                var cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                if (cause instanceof Error err) {
                    throw err;
                }
                throw new CompletionException(cause);
            }
        }

        @Override
        public CompletionStage<Object> handleAsync(DispatchContext context, Object params) {
            var parsed = parseCallParams(params);
            if (parsed == null) return CompletableFuture.completedFuture(invalidRequest("Invalid params"));
            if (parsed.name().isBlank()) return CompletableFuture.completedFuture(invalidRequest("Missing tool name"));
            if (parsed.name().length() > 64)
                return CompletableFuture.completedFuture(invalidRequest("Tool name exceeds maximum length (SEP-986)"));

            var handler = registry.get(parsed.name());
            if (handler == null) return CompletableFuture.completedFuture(methodNotFound("Method not found"));
            var extId = handler.descriptor().extensionId();
            if (extId != null && !context.isExtensionEnabled(extId))
                return CompletableFuture.completedFuture(methodNotFound("Method not found"));

            var validationError = validateInput(handler.descriptor().inputSchema(), parsed.args());
            if (validationError != null) return CompletableFuture.completedFuture(invalidRequest(validationError));

            sendLoggingIfEnabled(context, parsed.name(), "started");

            var progressToken = parseProgressToken(parsed.meta());
            var request = ToolRequest.builder()
                    .name(parsed.name())
                    .arguments(parsed.args() != null ? parsed.args() : Collections.emptyMap())
                    .meta(parsed.meta())
                    .progressToken(progressToken)
                    .payloadDeserializer(payloadDeserializer)
                    .inputResponses(parsed.inputResponses())
                    .requestState(parsed.requestState())
                    .build();

            CompletionStage<? extends ToolResult> toolStage;
            try {
                toolStage = handler.handleAsync(context, request);
            } catch (InvalidArgumentException e) {
                return CompletableFuture.completedFuture(
                        invalidRequest("invalid argument '" + e.argName() + "': " + e.getMessage()));
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }

            // isDone fast-path: if already complete, skip executor re-dispatch (inline on current thread).
            // A stage completing after this check merely takes the executor hop — benign, not a TOCTOU race.
            var done = toolStage.toCompletableFuture().isDone();
            var executor = done ? (Executor) Runnable::run : context.server().executor();
            return toolStage.handleAsync(
                    (toolResult, e) -> {
                        if (e != null) {
                            var cause =
                                    e instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : e;
                            if (cause instanceof InvalidArgumentException ia) {
                                return invalidRequest("invalid argument '" + ia.argName() + "': " + ia.getMessage());
                            }
                            throw new CompletionException(cause);
                        }
                        sendLoggingIfEnabled(context, parsed.name(), "completed");
                        validateOutput(handler.descriptor().outputSchema(), toolResult);
                        return context.responseMapper().callToolResult(serializeStructured(toolResult));
                    },
                    executor);
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
                @Nullable String requestState) {}

        private @Nullable CallParams parseCallParams(Object params) {
            if (params instanceof Map<?, ?> map) {
                var json = JsonRpcCodec.writeValueAsString(map);
                var typed = ProtocolCodecUtil.decodeWithCodec(json, CallToolRequestParams.class);
                var name = typed.name();
                if (name == null) return null;
                var inputResponses = extractInputResponsesFromMap(map.get("inputResponses"));
                var requestState = map.get("requestState") instanceof String s ? s : null;
                return new CallParams(name, typed.arguments(), typed._meta(), inputResponses, requestState);
            }
            if (params instanceof CallToolRequestParams p) {
                var name = p.name();
                if (name == null) return null;
                return new CallParams(name, p.arguments(), p._meta(), p.inputResponses(), p.requestState());
            }
            return null;
        }

        private static @Nullable Map<String, JsonNode> extractInputResponsesFromMap(@Nullable Object raw) {
            if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) return null;
            var result = new java.util.LinkedHashMap<String, JsonNode>();
            for (var entry : rawMap.entrySet()) {
                if (entry.getKey() instanceof String k) {
                    result.put(k, ProtocolCodecUtil.parseJsonNode(JsonRpcCodec.writeValueAsString(entry.getValue())));
                }
            }
            return result.isEmpty() ? null : result;
        }

        private @Nullable String validateInput(@Nullable JsonNode schema, @Nullable Map<String, JsonNode> args) {
            if (schema == null) return null;
            var argumentsNode = JsonNodeFactory.instance.objectNode();
            if (args != null) {
                argumentsNode.setAll(args);
            }
            var errors = inputValidator.validate(schema, argumentsNode);
            if (errors.isEmpty()) return null;
            return joinMessages(errors);
        }

        /**
         * Serializes non-tree structured values into {@link RawJson} via the configured serde,
         * so response mappers stay serde-free. {@link JsonNode} and {@link RawJson} pass through.
         * Maps carrying {@link JsonNode} values are serialized with Jackson regardless of the
         * configured serde — a non-Jackson serde cannot encode Jackson trees.
         */
        private ToolResult serializeStructured(ToolResult result) {
            if (result instanceof ToolResult.WithMeta wm) {
                var inner = serializeStructured(wm.inner());
                return inner == wm.inner() ? result : new ToolResult.WithMeta(inner, wm.meta());
            }
            if (!(result instanceof ToolResult.Success s)) return result;
            var sv = s.structuredValue();
            if (sv == null || sv instanceof RawJson || sv instanceof JsonNode) return result;
            var json = containsJsonNodes(sv) ? JsonUtils.writeString(sv) : payloadSerializer.serialize(sv);
            return new ToolResult.Success(RawJson.of(json), s.content());
        }

        private static boolean containsJsonNodes(Object structuredValue) {
            return structuredValue instanceof Map<?, ?> map
                    && map.values().stream().anyMatch(v -> v instanceof JsonNode);
        }

        private void validateOutput(@Nullable JsonNode schema, ToolResult result) {
            if (schema == null || outputValidator == JsonSchemaValidator.NOOP) return;
            var inner = result instanceof ToolResult.WithMeta wm ? wm.inner() : result;
            if (!(inner instanceof ToolResult.Success s)) return;
            var contentNode = structuredValueAsObjectNode(s.structuredValue());
            if (contentNode == null) return;
            var errors = outputValidator.validate(schema, contentNode);
            if (!errors.isEmpty()) {
                logger.debug("Tool output failed schema validation (advisory only): {}", joinMessages(errors));
            }
        }

        private @Nullable JsonNode structuredValueAsObjectNode(@Nullable Object structuredValue) {
            if (structuredValue instanceof RawJson rj) {
                var node = JsonUtils.parse(rj.json());
                return node.isObject() ? node : null;
            }
            if (structuredValue instanceof JsonNode node) {
                return node.isObject() ? node : null;
            }
            if (structuredValue instanceof java.util.Map<?, ?> map) {
                var contentNode = JsonNodeFactory.instance.objectNode();
                for (var entry : map.entrySet()) {
                    if (entry.getKey() instanceof String k) {
                        var v = entry.getValue();
                        if (v instanceof JsonNode jn) {
                            contentNode.set(k, jn);
                        } else if (v != null) {
                            contentNode.set(k, ProtocolCodecUtil.parseJsonNode(JsonRpcCodec.writeValueAsString(v)));
                        }
                    }
                }
                return contentNode;
            }
            return null;
        }

        private static String joinMessages(List<SchemaValidationError> errors) {
            return errors.stream().map(SchemaValidationError::message).collect(Collectors.joining("; "));
        }

        private void sendLoggingIfEnabled(DispatchContext context, String toolName, String status) {
            var session = context.session();
            if (session == null) return;
            var level = context.getLoggingLevel();
            if (level == null) return;
            context.server().log(session, level, "tachyon.tools", Map.of("tool", toolName, "status", status));
        }
    }
}
