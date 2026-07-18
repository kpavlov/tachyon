/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

class StatefulMcpSdkTest extends AbstractStatefulMcpE2eTest implements McpSdkContract {

    @Override
    public int port() {
        return port;
    }
}
