/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.conformance;

import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.TachyonServer;

class EdgeConformanceServer extends AbstractConformanceServer {

    @Override
    protected Server createServer() {
        return TachyonServer.builder().network(n -> n.host("localhost")).build();
    }
}
