/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.conformance;

import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.TachyonMcpServer;

/**
 * Test against the latest stable version of MCP conformance tests
 */
class DefaultServerConformanceIT extends AbstractServerConformanceIT {

    DefaultServerConformanceIT() {
        super("0.1.16");
    }

    @Override
    protected McpServer createServer() {
        return TachyonMcpServer.builder().host("localhost").build();
    }
}
