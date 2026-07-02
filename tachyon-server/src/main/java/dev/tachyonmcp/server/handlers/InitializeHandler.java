/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeRequestParams;
import dev.tachyonmcp.runtime.Extension;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.domain.InitializeResponse;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.session.DispatchContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class InitializeHandler implements RpcMethodHandler {

    private static final String MCP_VERSION = "2025-11-25";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Server server;
    private final List<ServerExtension> extensions;

    public InitializeHandler(Server server, List<ServerExtension> extensions) {
        this.server = server;
        this.extensions = extensions;
    }

    @Override
    public String method() {
        return "initialize";
    }

    @Override
    public Object handle(DispatchContext context, Object params) {
        var capabilities = server.resolveCapabilities();

        negotiateExtensions(context, params);
        var negotiatedExtensions = buildNegotiatedExtensions(context);

        final var serverConfig = server.config();

        var domainResponse = new InitializeResponse(
                MCP_VERSION,
                capabilities,
                serverConfig.identity(),
                serverConfig.identity().instructions(),
                negotiatedExtensions.isEmpty() ? null : negotiatedExtensions);

        return context.responseMapper().initializeResult(domainResponse);
    }

    private void negotiateExtensions(DispatchContext context, Object params) {
        var clientExtensions = extractClientExtensions(params);
        for (var ext : extensions) {
            if (clientExtensions.containsKey(ext.extensionId())) {
                context.enableExtension(ext.extensionId());
                var clientSettings = clientExtensions.get(ext.extensionId());
                ext.onConnectionInit(context, asMap(clientSettings));
            }
        }
    }

    private Map<String, JsonNode> buildNegotiatedExtensions(DispatchContext context) {
        return extensions.stream()
                .filter(e -> context.isExtensionEnabled(e.extensionId()))
                .collect(Collectors.toMap(Extension::extensionId, ServerExtension::serverSettings));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, JsonNode> extractClientExtensions(Object params) {
        if (params instanceof InitializeRequestParams initParams) {
            if (initParams.capabilities() == null) {
                return Map.of();
            }
            var clientExtensions = initParams.capabilities().extensions();
            return clientExtensions != null ? clientExtensions : Map.of();
        }
        if (params instanceof Map<?, ?> map) {
            var caps = map.get("capabilities");
            if (caps instanceof Map<?, ?> capsMap) {
                var exts = capsMap.get("extensions");
                if (exts instanceof Map<?, ?> extMap) {
                    var result = new LinkedHashMap<String, JsonNode>();
                    for (var entry : extMap.entrySet()) {
                        result.put(entry.getKey().toString(), MAPPER.valueToTree(entry.getValue()));
                    }
                    return result;
                }
            }
        }
        return Map.of();
    }

    private static Map<String, JsonNode> asMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, JsonNode>();
        for (var entry : node.properties()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
