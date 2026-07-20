/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.ProtocolCodecUtil;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.GetPromptRequestParams;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.config.FeatureConfig;
import dev.tachyonmcp.server.config.Mode;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.features.AbstractRegistry;
import dev.tachyonmcp.server.features.HandlerFutures;
import dev.tachyonmcp.server.features.ListRequests;
import dev.tachyonmcp.server.json.JsonSchemaUtils;
import dev.tachyonmcp.server.json.JsonSchemaValidator;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

@InternalApi
public class DefaultPromptRegistry extends AbstractRegistry<PromptDescriptor, PromptEntry> implements PromptRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPromptRegistry.class);

    private final JsonSchemaValidator validator;
    private final FeatureConfig config;

    /**
     * Creates a prompt registry with the specified schema validator and feature configuration.
     *
     * @param validator the validator used for prompt input schemas
     * @param config the feature configuration governing registry behavior and page size
     */
    public DefaultPromptRegistry(JsonSchemaValidator validator, FeatureConfig config) {
        super(config.pageSize());
        this.validator = validator;
        this.config = config;
    }

    /**
     * Registers a prompt unless prompt support is disabled by the configured mode.
     *
     * @param descriptor the prompt descriptor to register
     * @param handler the handler invoked for the prompt
     * @return this registry
     */
    @Override
    public Prompts register(PromptDescriptor descriptor, PromptHandler handler) {
        if (config.mode() == Mode.OFF) {
            logger.debug("Prompt '{}' not registered: prompts capability is OFF", descriptor.name());
            return this;
        }
        addItem(PromptEntry.of(descriptor, handler));
        return this;
    }

    /**
     * Removes the registered prompt with the specified name.
     *
     * @param name the name of the prompt to remove
     * @return {@code true} if a prompt was removed, {@code false} if no matching prompt was registered
     */
    @Override
    public boolean unregister(String name) {
        return removeItem(name);
    }

    /**
     * Finds a registered prompt descriptor by name.
     *
     * @param name the prompt name
     * @return the matching prompt descriptor, or an empty optional if no prompt is registered with that name
     */
    @Override
    public Optional<PromptDescriptor> find(String name) {
        var entry = get(name);
        return entry != null ? Optional.of(entry.descriptor()) : Optional.empty();
    }

    /**
     * Returns all registered prompt descriptors sorted by name.
     *
     * @return the registered prompt descriptors in ascending name order
     */
    @Override
    public List<PromptDescriptor> descriptors() {
        return getAll().stream()
                .map(PromptEntry::descriptor)
                .sorted(Comparator.comparing(PromptDescriptor::name))
                .toList();
    }

    /**
     * Registers the RPC handlers for listing and retrieving prompts.
     *
     * @param registry the map to populate with prompt RPC handlers
     */
    public void registerHandlers(Map<String, RpcMethodHandler> registry) {
        registry.put("prompts/list", new PromptsListHandler(this));
        registry.put("prompts/get", new PromptsGetHandler(this, validator));
    }

    private record PromptsListHandler(DefaultPromptRegistry registry) implements RpcMethodHandler {

        @Override
        public String method() {
            return "prompts/list";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var limit = ListRequests.parseLimit(params);
            var cursor = ListRequests.parseCursor(params);
            var paginated = registry.list(limit, cursor, descriptor -> {
                var extId = descriptor.extensionId();
                return extId == null || context.isExtensionEnabled(extId);
            });
            if (!paginated.cursorValid()) {
                return JsonRpcErrors.invalidParams("Invalid cursor");
            }

            var descriptors = paginated.items();
            return context.responseMapper().listPromptsResult(descriptors, paginated.nextCursor());
        }
    }

    private record PromptsGetHandler(DefaultPromptRegistry registry, JsonSchemaValidator validator)
            implements RpcMethodHandler {

        private static final Logger logger = LoggerFactory.getLogger(PromptsGetHandler.class);

        @Override
        public String method() {
            return "prompts/get";
        }

        @Override
        public Object handle(DispatchContext context, Object params) throws Exception {
            var name = extractParamName(params);
            if (name == null) return JsonRpcErrors.invalidRequest("Missing prompt name");
            var entry = registry.get(name);
            if (entry == null) return JsonRpcErrors.invalidRequest("Prompt not found");
            var extId = entry.descriptor().extensionId();
            if (extId != null && !context.isExtensionEnabled(extId)) {
                return JsonRpcErrors.invalidRequest("Prompt not found");
            }
            var inputSchema = entry.descriptor().inputSchema();
            if (inputSchema != null) {
                Map<String, JsonNode> argsMap;
                try {
                    argsMap = extractArgumentsMap(params);
                } catch (RuntimeException e) {
                    return JsonRpcErrors.invalidParams("Invalid arguments");
                }
                var error = JsonSchemaUtils.validateArguments(validator, inputSchema, argsMap);
                if (error != null) return JsonRpcErrors.invalidParams(error);
            }

            var request = new PromptRequest(
                    extractArguments(params),
                    extractInputResponsesFromParams(params),
                    extractRequestStateFromParams(params));

            // Runs on the dispatcher's virtual thread; blocking to join the handler is the SPI contract.
            PromptResult result;
            try {
                result = HandlerFutures.joinInterruptibly(entry.handler().handleAsync(context, request));
            } catch (Exception e) {
                logger.error("Prompt handler error for '{}'", name, e);
                return JsonRpcErrors.internalError("Prompt handler failed");
            }
            return switch (result) {
                case PromptResult.Messages m -> {
                    var messages = m.messages() != null ? m.messages() : List.<PromptMessage>of();
                    yield context.responseMapper()
                            .getPromptResult(entry.descriptor().description(), messages);
                }
                case PromptResult.InputRequired ir ->
                    context.responseMapper().inputRequiredResult(ir.inputRequests(), ir.requestState());
            };
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
            return params instanceof Map<?, ?> map
                    ? ListRequests.extractInputResponses(map.get("inputResponses"))
                    : null;
        }

        private static @Nullable String extractRequestStateFromParams(Object params) {
            if (!(params instanceof Map<?, ?> map)) return null;
            return map.get("requestState") instanceof String s ? s : null;
        }
    }
}
