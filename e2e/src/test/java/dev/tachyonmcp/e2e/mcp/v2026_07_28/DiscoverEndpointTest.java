/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp.v2026_07_28;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.mcp.AbstractStatelessMcpE2eTest;
import dev.tachyonmcp.testkit.Mcp20260728Client;
import dev.tachyonmcp.testkit.McpTestClients;
import org.junit.jupiter.api.Test;

class DiscoverEndpointTest extends AbstractStatelessMcpE2eTest<Mcp20260728Client> {

    @Override
    protected Mcp20260728Client createTestClient() {
        return createTestClient(port);
    }

    @Override
    protected Mcp20260728Client createTestClient(int port) {
        return McpTestClients.latest(port);
    }

    @Test
    void discoversTheModernProtocol() throws Exception {
        startEmptyServer();
        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {
                      "jsonrpc": "2.0",
                      "id": "discover-1",
                      "method": "server/discover"
                    }
                    """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("MCP-Session-Id")).isEmpty();
            assertThatJson(response.body())
                    .isEqualTo(
                            // language=json
                            """
                    {
                      "jsonrpc": "2.0",
                      "id": "discover-1",
                      "result": {
                        "supportedVersions": ["2026-07-28", "2025-11-25"],
                        "capabilities": {},
                        "serverInfo": {"name": "tachyon-mcp", "version": "0.1"},
                        "_meta": {"io.modelcontextprotocol/serverInfo": {"name": "tachyon-mcp", "version": "0.1"}},
                        "resultType": "complete",
                        "ttlMs": 0,
                        "cacheScope": "public"
                      }
                    }
                    """);
        }
    }
}
