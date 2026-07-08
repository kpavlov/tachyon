/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.ProtocolCodecUtil;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.GetPromptRequestParams;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.features.HandlerFutures;
import dev.tachyonmcp.server.features.ListRequests;
import dev.tachyonmcp.server.features.Registry;
import dev.tachyonmcp.server.json.JsonSchemaUtils;
import dev.tachyonmcp.server.json.JsonSchemaValidator;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

public class PromptRegistry extends Registry<PromptEntry> {

    private final JsonSchemaValidator validator;

    public PromptRegistry(JsonSchemaValidator validator) {
        this.validator = validator;
    }

    public void add(PromptDescriptor descriptor, List<PromptMessage> messages) {
        super.add(PromptEntry.of(descriptor, args -> messages));
    }

    public void add(PromptDescriptor descriptor, PromptHandler handler) {
        super.add(PromptEntry.of(descriptor, handler));
    }

    public void add(PromptDescriptor descriptor, InputRequiredPromptHandler handler) {
        super.add(new PromptEntry(descriptor, handler));
    }

    public void registerHandlers(Map<String, RpcMethodHandler> registry) {
        registry.put("prompts/list", new PromptsListHandler(this));
        registry.put("prompts/get", new PromptsGetHandler(this, validator));
    }

    private record PromptsListHandler(Registry<PromptEntry> registry) implements RpcMethodHandler {

        @Override
        public String method() {
            return "prompts/list";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var limit = ListRequests.parseLimit(params);
            var cursor = ListRequests.parseCursor(params);
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
            implements RpcMethodHandler {

        private static final Logger logger = LoggerFactory.getLogger(PromptsGetHandler.class);

        @Override
        public String method() {
            return "prompts/get";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            try {
                return HandlerFutures.joinInterruptibly(handleAsync(context, params));
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }

        @Override
        public CompletionStage<Object> handleAsync(DispatchContext context, Object params) {
            var name = extractParamName(params);
            if (name == null) {
                return CompletableFuture.completedFuture(JsonRpcErrors.invalidRequest("Missing prompt name"));
            }
            var entry = registry.get(name);
            if (entry == null) {
                return CompletableFuture.completedFuture(JsonRpcErrors.invalidRequest("Prompt not found"));
            }
            var extId = entry.descriptor().extensionId();
            if (extId != null && !context.isExtensionEnabled(extId)) {
                return CompletableFuture.completedFuture(JsonRpcErrors.invalidRequest("Prompt not found"));
            }
            var inputSchema = entry.descriptor().inputSchema();
            if (inputSchema != null) {
                Map<String, JsonNode> argsMap;
                try {
                    argsMap = extractArgumentsMap(params);
                } catch (RuntimeException e) {
                    return CompletableFuture.completedFuture(JsonRpcErrors.invalidParams("Invalid arguments"));
                }
                var error = JsonSchemaUtils.validateArguments(validator, inputSchema, argsMap);
                if (error != null) return CompletableFuture.completedFuture(JsonRpcErrors.invalidParams(error));
            }

            var request = new PromptRequest(
                    extractArguments(params),
                    extractInputResponsesFromParams(params),
                    extractRequestStateFromParams(params));

            CompletionStage<? extends PromptHandlerResult> stage;
            try {
                stage = entry.handler().handleAsync(context, request);
            } catch (Exception e) {
                logger.error("Prompt handler error for '{}'", name, e);
                return CompletableFuture.completedFuture(JsonRpcErrors.internalError("Prompt handler failed"));
            }
            return HandlerFutures.completeOn(stage, context, (result, e) -> {
                if (e != null) {
                    logger.error("Prompt handler error for '{}'", name, e);
                    return JsonRpcErrors.internalError("Prompt handler failed");
                }
                return switch ((PromptHandlerResult) result) {
                    case PromptHandlerResult.Messages m -> {
                        var messages = m.messages() != null ? m.messages() : List.<PromptMessage>of();
                        yield context.responseMapper()
                                .getPromptResult(entry.descriptor().description(), messages);
                    }
                    case PromptHandlerResult.InputRequired ir ->
                        context.responseMapper().inputRequiredResult(ir.inputRequests(), ir.requestState());
                };
            });
        }

        private static @Nullable String extractArguments(Object params) {
            if (params instanceof GetPromptRequestParams p && p.arguments() != null) {
                return JsonRpcCodec.writeValueAsString(p.arguments());
            }
            if (params instanceof Map<?, ?> map && map.get("arguments") instanceof Map<?, ?> args) {
                return JsonRpcCodec.writeValueAsString(args);
            }
            return null;
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

        private static @Nullable Map<String, JsonNode> extractInputResponsesFromParams(Object params) {
            if (!(params instanceof Map<?, ?> map)) return null;
            var raw = map.get("inputResponses");
            if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) return null;
            var result = new LinkedHashMap<String, JsonNode>();
            for (var entry : rawMap.entrySet()) {
                if (entry.getKey() instanceof String k) {
                    result.put(k, ProtocolCodecUtil.parseJsonNode(JsonRpcCodec.writeValueAsString(entry.getValue())));
                }
            }
            return result.isEmpty() ? null : result;
        }

        private static @Nullable String extractRequestStateFromParams(Object params) {
            if (!(params instanceof Map<?, ?> map)) return null;
            return map.get("requestState") instanceof String s ? s : null;
        }
    }
}
