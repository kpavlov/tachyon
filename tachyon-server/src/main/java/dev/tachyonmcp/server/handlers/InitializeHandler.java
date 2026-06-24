/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.ServerInfoMapper;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeResult;
import dev.tachyonmcp.runtime.Extension;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.extensions.McpExtension;
import dev.tachyonmcp.server.session.McpContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

public final class InitializeHandler implements McpMethodHandler {

    private final McpServer mcpServer;
    private final List<McpExtension> extensions;

    public InitializeHandler(McpServer mcpServer, List<McpExtension> extensions) {
        this.mcpServer = mcpServer;
        this.extensions = extensions;
    }

    @Override
    public String method() {
        return "initialize";
    }

    @Override
    public Object handle(McpContext context, Object params) {
        var negotiatedVersion = "2025-11-25";
        context.session().protocolVersion(negotiatedVersion);

        var capabilities = mcpServer.resolveCapabilities();

        var resultCapabilitiesBuilder = ServerInfoMapper.toServerCapabilities(capabilities);

        negotiateExtensions(context, params);
        var negotiatedExtensions = buildNegotiatedExtensions(context);
        if (!negotiatedExtensions.isEmpty()) {
            resultCapabilitiesBuilder.extensions(negotiatedExtensions);
        }

        final var serverConfig = mcpServer.config();

        final var implementation = ServerInfoMapper.toImplementation(serverConfig.identity());

        return new InitializeResult(
                negotiatedVersion,
                resultCapabilitiesBuilder.build(),
                implementation,
                serverConfig.identity().instructions(),
                null,
                null);
    }

    private void negotiateExtensions(McpContext context, Object params) {
        var clientExtensions = extractClientExtensions(params);
        for (var ext : extensions) {
            if (clientExtensions.containsKey(ext.extensionId())) {
                context.enableExtension(ext.extensionId());
                var clientSettings = clientExtensions.get(ext.extensionId());
                ext.onConnectionInit(context, asMap(clientSettings));
            }
        }
    }

    private Map<String, JsonNode> buildNegotiatedExtensions(McpContext context) {
        return extensions.stream()
                .filter(e -> context.isExtensionEnabled(e.extensionId()))
                .collect(Collectors.toMap(Extension::extensionId, McpExtension::serverSettings));
    }

    private static Map<String, JsonNode> extractClientExtensions(Object params) {
        if (!(params instanceof InitializeRequestParams initParams)) {
            return Map.of();
        }
        if (initParams.capabilities() == null) {
            return Map.of();
        }
        var clientExtensions = initParams.capabilities().extensions();
        return clientExtensions != null ? clientExtensions : Map.of();
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
