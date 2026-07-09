/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.conformance;

import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.TachyonServer;

class EdgeConformanceServer extends AbstractConformanceServer {

    @Override
    protected Server createServer(boolean isStateful) {
        return TachyonServer.builder().session(s -> s.enabled(isStateful)).build();
    }
}
