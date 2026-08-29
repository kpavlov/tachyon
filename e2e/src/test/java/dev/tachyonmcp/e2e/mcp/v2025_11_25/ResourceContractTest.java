/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp.v2025_11_25;

import dev.tachyonmcp.e2e.mcp.AbstractResourceContractTest;
import dev.tachyonmcp.testkit.McpClient;

class ResourceContractTest extends AbstractResourceContractTest {

    @Override
    protected McpClient readyClient() throws Exception {
        var client = createTestClient();
        client.initialize();
        return client;
    }

    @Override
    protected int resourceNotFoundErrorCode() {
        return -32002;
    }
}
