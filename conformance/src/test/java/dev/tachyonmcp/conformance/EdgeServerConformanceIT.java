/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.conformance;

import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.TachyonServer;

/**
 * Test against the latest edge version of MCP conformance tests
 */
class EdgeServerConformanceIT extends AbstractServerConformanceIT {

    EdgeServerConformanceIT() {
        super("0.2.0-alpha.4");
    }

    @Override
    protected McpServer createServer() {
        return TachyonServer.builder().host("localhost").build();
    }
}
