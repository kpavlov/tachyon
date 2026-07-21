/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 removed protocol-level sessions (SEP-2575): every request must be dispatchable
 * with no {@code MCP-Session-Id} header at all, regardless of the server's own stateful/stateless
 * configuration. Regression test for {@code McpDispatcher} only bypassing the session requirement
 * for {@code server/discover}/{@code ping} instead of every method under this protocol version.
 */
class StatelessDispatchTest extends AbstractStatelessMcpE2eTest {

    @Test
    void toolsCallSucceedsWithNoSession() throws Exception {
        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "tools/call",
                      "params": {"name": "echo", "arguments": {"message": "hi"}}
                    }
                    """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("MCP-Session-Id")).isEmpty();
            assertThatJson(response.body()).inPath("$.result.content[0].text").isEqualTo("hi");
        }
    }

    @Test
    void toolsListSucceedsWithNoSession() throws Exception {
        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc": "2.0", "id": 2, "method": "tools/list"}
                    """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("MCP-Session-Id")).isEmpty();
            assertThatJson(response.body()).inPath("$.result.tools[0].name").isEqualTo("echo");
        }
    }
}
