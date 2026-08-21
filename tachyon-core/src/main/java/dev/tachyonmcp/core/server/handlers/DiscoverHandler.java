/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import dev.tachyonmcp.core.protocol.Protocol;
import dev.tachyonmcp.core.protocol.Protocols;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.Comparator;

/** Handles the mandatory modern MCP {@code server/discover} request. */
public final class DiscoverHandler implements RpcMethodHandler<Object, Object> {

    private final ServerEngine server;

    public DiscoverHandler(ServerEngine server) {
        this.server = server;
    }

    @Override
    public String method() {
        return "server/discover";
    }

    @Override
    public Object handle(DispatchContext context, Object params) {
        var supportedVersions = Protocols.list().stream()
                .map(Protocol::versionString)
                .sorted(Comparator.reverseOrder())
                .toList();
        return context.responseMapper()
                .discoverResult(
                        supportedVersions,
                        server.resolveCapabilities(),
                        server.config().identity(),
                        ExtensionNegotiator.registeredExtensions(server.extensions(), context));
    }
}
