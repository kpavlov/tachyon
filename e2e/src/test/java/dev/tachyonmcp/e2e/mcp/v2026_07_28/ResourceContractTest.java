/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp.v2026_07_28;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.mcp.AbstractResourceContractTest;
import dev.tachyonmcp.testkit.Mcp20260728Client;
import dev.tachyonmcp.testkit.McpTestClients;
import org.junit.jupiter.api.Test;

class ResourceContractTest extends AbstractResourceContractTest<Mcp20260728Client> {

    @Override
    protected Mcp20260728Client createTestClient() {
        return createTestClient(port);
    }

    @Override
    protected Mcp20260728Client createTestClient(int port) {
        return McpTestClients.latest(port);
    }

    @Override
    protected Mcp20260728Client readyClient() {
        return createModernTestClient();
    }

    @Override
    protected int resourceNotFoundErrorCode() {
        return -32602;
    }

    /**
     * Not part of the shared contract: under 2025-11-25 an invalid URI is a domain-level {@code
     * -32602} JSON-RPC error (see {@code v2025_11_25.ResourceTest.shouldRejectInvalidResourceUri}).
     * Under 2026-07-28 the same URI's embedded space trips the SEP-2243 {@code Mcp-Name}
     * header-mirror check first, rejecting at the transport layer before URI validation ever runs —
     * a genuinely different code path, not just a different error code.
     */
    @Test
    void shouldRejectResourceUriContainingSpaceAtTransportLayer() throws Exception {
        startEmptyServer();

        try (var client = readyClient()) {
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://bad uri"}}
                """);

            assertThat(response.statusCode()).isEqualTo(400);
        }
    }
}
