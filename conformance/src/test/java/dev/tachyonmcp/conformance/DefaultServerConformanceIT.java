/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.conformance;

import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.TachyonServer;
import org.junit.jupiter.api.Disabled;

/**
 * Test against the latest stable version of MCP conformance tests
 */
@Disabled("Using Edge version")
class DefaultServerConformanceIT extends AbstractServerConformanceIT {

    DefaultServerConformanceIT() {
        super("0.1.16");
    }

    @Override
    protected McpServer createServer() {
        return TachyonServer.builder().network(n -> n.host("localhost")).build();
    }
}
