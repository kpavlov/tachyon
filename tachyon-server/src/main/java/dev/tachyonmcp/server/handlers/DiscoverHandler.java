/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.server.session.DispatchContext;
import java.util.Comparator;

/** Handles the mandatory modern MCP {@code server/discover} request. */
public final class DiscoverHandler implements RpcMethodHandler {

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
                .map(protocol -> protocol.versionString())
                .sorted(Comparator.reverseOrder())
                .toList();
        return context.responseMapper()
                .discoverResult(
                        supportedVersions,
                        server.resolveCapabilities(),
                        server.config().identity());
    }
}
