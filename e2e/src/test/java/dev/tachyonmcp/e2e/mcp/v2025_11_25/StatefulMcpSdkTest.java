/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp.v2025_11_25;

import dev.tachyonmcp.e2e.mcp.McpSdkContract;

class StatefulMcpSdkTest extends AbstractStatefulMcpE2eTest implements McpSdkContract {

    @Override
    public int port() {
        return port;
    }
}
