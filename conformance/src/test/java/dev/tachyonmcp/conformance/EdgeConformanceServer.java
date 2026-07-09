/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.conformance;

import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.internal.ServerEngine;

class EdgeConformanceServer extends AbstractConformanceServer {

    @Override
    protected ServerEngine createServer(boolean isStateful) {
        return (ServerEngine)
                TachyonServer.builder().session(s -> s.enabled(isStateful)).build();
    }
}
