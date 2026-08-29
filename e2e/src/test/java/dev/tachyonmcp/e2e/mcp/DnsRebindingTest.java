/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.testkit.Mcp20251125Client;
import dev.tachyonmcp.testkit.McpClient;
import org.junit.jupiter.api.Test;

/**
 * Verifies DNS-rebinding protection and, crucially, that a rejected request is closed with a
 * {@code Connection: close} header.
 *
 * <p>Regression guard for a 50%-flaky conformance failure on Linux: the server used to reject
 * with a default keep-alive 403 and then close the socket anyway. Clients (e.g. undici) pooled
 * that socket, raced the next request onto it, and intermittently saw "other side closed".
 * Signalling {@code Connection: close} stops the client from reusing the socket.
 */
class DnsRebindingTest extends AbstractStatelessMcpE2eTest<McpClient> {

    @Override
    protected Mcp20251125Client createTestClient() {
        return createTestClient(port);
    }

    @Override
    protected Mcp20251125Client createTestClient(int port) {
        return new Mcp20251125Client(port);
    }

    // language=JSON
    private static final String INIT_BODY = """
            {
              "jsonrpc":"2.0",
              "id":1,
              "method":"initialize",
              "params":{
                "protocolVersion":"2025-11-25",
                "capabilities":{},
                "clientInfo":{"name":"test","version":"1.0"}
              }
            }
            """;

    @Test
    void rejectsNonLocalhostOriginAndSignalsClose() throws Exception {
        try (var client = createTestClient()) {
            var response = client.postWithOrigin("http://evil.example.com", INIT_BODY);

            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.headers().firstValue("connection"))
                    .as("rejected requests must signal Connection: close so the client does not pool the socket")
                    .hasValueSatisfying(v -> assertThat(v).isEqualToIgnoringCase("close"));
        }
    }

    @Test
    void acceptsLocalhostOrigin() throws Exception {
        try (var client = createTestClient()) {
            var response = client.postWithOrigin("http://localhost:" + port, INIT_BODY);

            assertThat(response.statusCode()).isEqualTo(200);
        }
    }
}
