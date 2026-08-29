/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp;

import static dev.tachyonmcp.testkit.McpHttpResponseAssert.assertThatResponse;

import dev.tachyonmcp.testkit.Mcp20251125Client;
import dev.tachyonmcp.testkit.McpClient;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins that {@code NetworkConfig.maxContentLength} actually reaches the
 * {@code HttpObjectAggregator}: a body past the 1 MiB default is refused, not parsed.
 */
class MaxContentLengthTest extends AbstractStatelessMcpE2eTest<McpClient> {

    @Override
    protected Mcp20251125Client createTestClient() {
        return createTestClient(port);
    }

    @Override
    protected Mcp20251125Client createTestClient(int port) {
        return new Mcp20251125Client(port);
    }

    @Test
    void rejectsBodyLargerThanMaxContentLength() throws Exception {
        var oversized = "x".repeat(2 * 1024 * 1024);
        // language=JSON
        var body = """
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"padding":"%s"}}
                """.formatted(oversized);

        var response = postMcpRequest(body, Map.of("Mcp-Method", "tools/list"));

        assertThatResponse(response).hasStatus(413);
    }
}
