/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.ProtocolCodecUtil;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.GetPromptRequestParams;
import dev.tachyonmcp.server.JsonSchemaValidator;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.SchemaValidationError;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.features.Registry;
import dev.tachyonmcp.server.features.tools.ToolRegistry;
import dev.tachyonmcp.server.session.McpContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

public class PromptRegistry extends Registry<PromptEntry> {

    private final JsonSchemaValidator validator;

    public PromptRegistry(JsonSchemaValidator validator) {
        this.validator = validator;
    }

    public void add(PromptDescriptor descriptor, List<PromptMessage> messages) {
        super.add(new PromptEntry(descriptor, args -> messages));
    }

    public void add(PromptDescriptor descriptor, PromptHandler handler) {
        super.add(new PromptEntry(descriptor, handler));
    }

    public void registerHandlers(Map<String, McpMethodHandler> registry) {
        registry.put("prompts/list", new PromptsListHandler(this));
        registry.put("prompts/get", new PromptsGetHandler(this, validator));
    }

    private record PromptsListHandler(Registry<PromptEntry> registry) implements McpMethodHandler {

        @Override
        public String method() {
            return "prompts/list";
        }

        @Override
        public Object handle(McpContext context, Object params) {
            var limit = ToolRegistry.parseLimit(params);
            var cursor = ToolRegistry.parseCursor(params);
            var paginated = registry.list(limit, cursor, e -> {
                var extId = e.descriptor().extensionId();
                return extId == null || context.isExtensionEnabled(extId);
            });

            var descriptors =
                    paginated.items().stream().map(PromptEntry::descriptor).toList();
            return context.responseMapper().listPromptsResult(descriptors, paginated.nextCursor());
        }
    }

    private record PromptsGetHandler(Registry<PromptEntry> registry, JsonSchemaValidator validator)
            implements McpMethodHandler {

        @Override
        public String method() {
            return "prompts/get";
        }

        @Override
        public Object handle(McpContext context, Object params) {
            var name = extractParamName(params);
            if (name == null) {
                return JsonRpcErrors.invalidRequest("Missing prompt name");
            }
            var entry = registry.get(name);
            if (entry == null) {
                return JsonRpcErrors.invalidRequest("Prompt not found");
            }
            var extId = entry.descriptor().extensionId();
            if (extId != null && !context.isExtensionEnabled(extId)) {
                return JsonRpcErrors.invalidRequest("Prompt not found");
            }
            var inputSchema = entry.descriptor().inputSchema();
            if (inputSchema != null) {
                var argsNode = JsonNodeFactory.instance.objectNode();
                Map<String, JsonNode> argsMap;
                try {
                    argsMap = extractArgumentsMap(params);
                } catch (RuntimeException e) {
                    return JsonRpcErrors.invalidParams("Invalid arguments");
                }
                if (argsMap != null) {
                    argsMap.forEach(argsNode::set);
                }
                var errors = validator.validate(inputSchema, argsNode);
                if (!errors.isEmpty()) {
                    return JsonRpcErrors.invalidParams(
                            errors.stream().map(SchemaValidationError::message).collect(Collectors.joining("; ")));
                }
            }
            java.util.List<PromptMessage> domainMessages;
            try {
                domainMessages = entry.handler().getMessages(extractArguments(params));
            } catch (Exception e) {
                return JsonRpcErrors.internalError(e.getMessage());
            }
            if (domainMessages == null) {
                domainMessages = List.of();
            }
            return context.responseMapper().getPromptResult(entry.descriptor().description(), domainMessages);
        }

        private static String extractArguments(Object params) {
            if (params instanceof GetPromptRequestParams p && p.arguments() != null) {
                return JsonRpcCodec.writeValueAsString(p.arguments());
            }
            if (params instanceof Map<?, ?> map && map.get("arguments") instanceof Map<?, ?> args) {
                return JsonRpcCodec.writeValueAsString(args);
            }
            return "";
        }

        private static @Nullable Map<String, JsonNode> extractArgumentsMap(Object params) {
            if (params instanceof GetPromptRequestParams p && p.arguments() != null) {
                return p.arguments();
            }
            if (params instanceof Map<?, ?> map) {
                var json = JsonRpcCodec.writeValueAsString(map);
                var typed = ProtocolCodecUtil.decodeWithCodec(json, GetPromptRequestParams.class);
                return typed.arguments();
            }
            return null;
        }

        private static @Nullable String extractParamName(Object params) {
            if (params instanceof GetPromptRequestParams p) {
                return p.name();
            }
            if (!(params instanceof Map<?, ?> map)) return null;
            if (map.get("name") instanceof String s) return s;
            return null;
        }
    }
}
