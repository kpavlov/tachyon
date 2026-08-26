/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e;

import static dev.tachyonmcp.testkit.McpHttpResponseAssert.assertThatResponse;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins that {@code NetworkConfig.maxContentLength} actually reaches the
 * {@code HttpObjectAggregator}: a body past the 1 MiB default is refused, not parsed.
 */
class MaxContentLengthTest extends AbstractStatelessMcpE2eTest {

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
