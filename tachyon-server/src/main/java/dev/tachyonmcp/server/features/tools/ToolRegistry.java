/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import static dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors.invalidRequest;
import static dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors.methodNotFound;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.ProtocolCodecUtil;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CallToolRequestParams;
import dev.tachyonmcp.server.JsonSchemaValidator;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.SchemaValidationError;
import dev.tachyonmcp.server.features.PaginatedResult;
import dev.tachyonmcp.server.session.McpContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

public class ToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);

    private final ConcurrentHashMap<String, ToolHandler<? extends ToolResult>> handlers = new ConcurrentHashMap<>();
    private final JsonSchemaValidator validator;

    private @Nullable Runnable onChange;

    private static final int DEFAULT_PAGE_SIZE = 50;

    public ToolRegistry(JsonSchemaValidator validator) {
        this.validator = validator;
    }

    public void onChange(@Nullable Runnable callback) {
        this.onChange = callback;
    }

    private void fireOnChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    public void register(ToolHandler<? extends ToolResult> handler) {
        Objects.requireNonNull(handler, "ToolHandler must not be null");
        var descriptor = handler.descriptor();
        Objects.requireNonNull(descriptor, "ToolDescriptor must not be null");
        var name = descriptor.name();
        validateName(name);
        handlers.put(name, handler);
        fireOnChange();
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

    public void remove(String name) {
        if (handlers.remove(name) != null) {
            fireOnChange();
        }
    }

    public @Nullable ToolHandler<? extends ToolResult> get(String name) {
        return handlers.get(name);
    }

    public @Nullable ToolDescriptor getDescriptor(String name) {
        var handler = handlers.get(name);
        return handler != null ? handler.descriptor() : null;
    }

    public Collection<ToolHandler<? extends ToolResult>> getAll() {
        return handlers.values();
    }

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

    public void registerHandlers(Map<String, McpMethodHandler> registry) {
        registry.put("tools/list", new ToolsListHandler(this));
        registry.put("tools/call", new ToolsCallHandler(this, validator));
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

    private record ToolsListHandler(ToolRegistry registry) implements McpMethodHandler {

        @Override
        public String method() {
            return "tools/list";
        }

        @Override
        public Object handle(McpContext context, Object params) {
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

    private record ToolsCallHandler(ToolRegistry registry, JsonSchemaValidator validator) implements McpMethodHandler {

        @Override
        public String method() {
            return "tools/call";
        }

        @Override
        public Object handle(McpContext context, Object params) throws Exception {
            var parsed = parseCallParams(params);
            if (parsed == null) return invalidRequest("Invalid params");
            if (parsed.name().isBlank()) return invalidRequest("Missing tool name");
            if (parsed.name().length() > 64) return invalidRequest("Tool name exceeds maximum length (SEP-986)");

            var handler = registry.get(parsed.name());
            if (handler == null) return methodNotFound("Method not found");
            var extId = handler.descriptor().extensionId();
            if (extId != null && !context.isExtensionEnabled(extId)) return methodNotFound("Method not found");

            var validationError = validateInput(handler.descriptor().inputSchema(), parsed.args());
            if (validationError != null) return invalidRequest(validationError);

            sendLoggingIfEnabled(context, parsed.name(), "started");

            var progressToken = parseProgressToken(parsed.meta());
            var request = ToolRequest.of(parsed.name(), parsed.args(), parsed.meta(), progressToken, null);

            try {
                var toolResult =
                        handler.handle(request, context).toCompletableFuture().join();
                sendLoggingIfEnabled(context, parsed.name(), "completed");
                validateOutput(handler.descriptor().outputSchema(), toolResult);
                return context.responseMapper().callToolResult(toolResult);
            } catch (CompletionException e) {
                var cause = e.getCause();
                if (cause instanceof Exception ex) throw ex;
                throw new RuntimeException(cause);
            }
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
                @Nullable Map<String, JsonNode> meta) {}

        private @Nullable CallParams parseCallParams(Object params) {
            if (params instanceof CallToolRequestParams p) {
                var name = p.name();
                if (name == null) return null;
                return new CallParams(name, p.arguments(), p._meta());
            }
            if (params instanceof Map<?, ?> map) {
                var json = JsonRpcCodec.writeValueAsString(map);
                var typed = ProtocolCodecUtil.decodeWithCodec(json, CallToolRequestParams.class);
                var name = typed.name();
                if (name == null) return null;
                return new CallParams(name, typed.arguments(), typed._meta());
            }
            return null;
        }

        private @Nullable String validateInput(@Nullable JsonNode schema, @Nullable Map<String, JsonNode> args) {
            if (schema == null) return null;
            var argumentsNode = JsonNodeFactory.instance.objectNode();
            if (args != null) {
                argumentsNode.setAll(args);
            }
            var errors = validator.validate(schema, argumentsNode);
            if (errors.isEmpty()) return null;
            return joinMessages(errors);
        }

        private void validateOutput(@Nullable JsonNode schema, ToolResult result) {
            if (schema == null || result.structuredContent() == null) return;
            var contentNode = JsonNodeFactory.instance.objectNode();
            contentNode.setAll(result.structuredContent());
            var errors = validator.validate(schema, contentNode);
            if (!errors.isEmpty()) {
                logger.debug("Tool output failed schema validation (advisory only): {}", joinMessages(errors));
            }
        }

        private static String joinMessages(List<SchemaValidationError> errors) {
            return errors.stream().map(SchemaValidationError::message).collect(Collectors.joining("; "));
        }

        private void sendLoggingIfEnabled(McpContext context, String toolName, String status) {
            var level = context.server().getLoggingLevel();
            if (level == null) return;
            context.server()
                    .mcpServer()
                    .log(
                            context.server().session(),
                            level,
                            "tachyon.tools",
                            Map.of("tool", toolName, "status", status));
        }
    }
}
