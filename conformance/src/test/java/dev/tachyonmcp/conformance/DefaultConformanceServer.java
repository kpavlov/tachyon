/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.conformance;

import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.TachyonServer;

class DefaultConformanceServer extends AbstractConformanceServer {

    @Override
    protected McpServer createServer() {
        return TachyonServer.builder().network(n -> n.host("localhost")).build();
    }
}
