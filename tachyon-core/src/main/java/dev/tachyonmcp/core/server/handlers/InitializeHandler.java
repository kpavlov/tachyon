/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import dev.tachyonmcp.core.protocol.ProtocolRequestMapper;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.McpProtocol;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.domain.InitializeResponse;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.server.session.DispatchContext;

/**
 * Handles MCP 2025-11-25's {@code initialize} handshake: maps the request, resolves server
 * capabilities, negotiates extensions once via {@link ExtensionNegotiator} (unlike 2026-07-28,
 * which redeclares extensions per request through {@code ExtensionNegotiationHandler}), and maps
 * the result back through the negotiated protocol's response mapper.
 */
public final class InitializeHandler implements RpcMethodHandler {

    private static final String MCP_VERSION = McpProtocol.VERSION;
    private final ServerEngine server;
    private final ExtensionNegotiator negotiator;

    public InitializeHandler(ServerEngine server, ExtensionNegotiator negotiator) {
        this.server = server;
        this.negotiator = negotiator;
    }

    @Override
    public String method() {
        return "initialize";
    }

    @Override
    public Object handle(DispatchContext context, Object params) {
        final ProtocolRequestMapper.InitializeRequest initializeRequest;
        try {
            initializeRequest = context.requestMapper().initialize(params);
        } catch (RequestMappingException e) {
            return e.error();
        }

        var capabilities = server.resolveCapabilities();

        negotiator.negotiate(context, initializeRequest.extensions());
        var registeredExtensions = negotiator.registeredExtensions(context);

        final var serverConfig = server.config();
        var domainResponse = new InitializeResponse(
                MCP_VERSION,
                capabilities,
                serverConfig.identity(),
                serverConfig.identity().instructions(),
                registeredExtensions.isEmpty() ? null : registeredExtensions);

        return context.responseMapper().initializeResult(domainResponse);
    }
}
