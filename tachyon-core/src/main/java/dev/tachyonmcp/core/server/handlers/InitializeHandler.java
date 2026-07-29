/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.runtime.Extension;
import dev.tachyonmcp.api.server.extensions.ExtensionSettings;
import dev.tachyonmcp.api.server.extensions.ServerExtension;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.McpProtocol;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.domain.InitializeResponse;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class InitializeHandler implements RpcMethodHandler {

    private static final String MCP_VERSION = McpProtocol.VERSION;
    private final ServerEngine server;
    private final List<ServerExtension> extensions;

    public InitializeHandler(ServerEngine server, List<ServerExtension> extensions) {
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

        try {
            negotiateExtensions(context, params);
        } catch (RequestMappingException e) {
            return e.error();
        }
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
        var clientExtensions = context.requestMapper().initialize(params).extensions();
        for (var ext : extensions) {
            if (clientExtensions.containsKey(ext.extensionId())) {
                context.enableExtension(ext.extensionId());
                var clientSettings = clientExtensions.get(ext.extensionId());
                ext.onConnectionInit(context, ExtensionSettings.of(clientSettings.asMap()));
            }
        }
    }

    private Map<String, JsonObject> buildNegotiatedExtensions(DispatchContext context) {
        return extensions.stream()
                .filter(e -> context.isExtensionEnabled(e.extensionId()))
                .collect(Collectors.toMap(
                        Extension::extensionId,
                        extension -> extension.serverSettings().values()));
    }
}
