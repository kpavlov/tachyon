/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp.v2026_07_28;

import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThat;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.domain.MissingRequiredClientCapabilityException;
import dev.tachyonmcp.e2e.mcp.AbstractStatelessMcpE2eTest;
import dev.tachyonmcp.testkit.Mcp20260728Client;
import dev.tachyonmcp.testkit.McpClient;
import dev.tachyonmcp.testkit.McpTestClients;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 {@code MissingRequiredClientCapabilityError} (-32021, SEP-2575): a server MUST
 * NOT rely on capabilities the client didn't declare in
 * {@code _meta.io.modelcontextprotocol/clientCapabilities}. A handler signals this by throwing
 * {@link MissingRequiredClientCapabilityException}; {@code DefaultToolRegistry} catches it and maps
 * it to the wire error, whose HTTP status (400) is now spec-tied via
 * {@code v2026_07_28.codecs.McpResponseMapper}.
 */
class MissingCapabilityTest extends AbstractStatelessMcpE2eTest<McpClient> {

    @Override
    protected Mcp20260728Client createTestClient() {
        return createTestClient(port);
    }

    @Override
    protected Mcp20260728Client createTestClient(int port) {
        return McpTestClients.latest(port);
    }

    @BeforeEach
    void registerFixtures() {
        startServerWith(s -> s.tools()
                .register(
                        tool -> tool.name("test_missing_capability")
                                .description("Requires the sampling capability")
                                .inputSchema("{\"type\": \"object\", \"properties\": {}}"),
                        (ctx, request) -> {
                            var meta = request.meta();
                            var capabilities =
                                    meta != null ? meta.get("io.modelcontextprotocol/clientCapabilities") : null;
                            var hasSampling = capabilities instanceof Map<?, ?> map && map.containsKey("sampling");
                            if (!hasSampling) {
                                throw new MissingRequiredClientCapabilityException(
                                        "Requires the 'sampling' capability", Map.of("sampling", Map.of()));
                            }
                            return ToolResult.text("sampling capability present");
                        }));
    }

    @Test
    void rejectsCallWithoutDeclaredCapability() throws Exception {
        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": "test_missing_capability", "arguments": {}}}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThat(response).isJsonRpcError().hasId(1).hasErrorCode(-32021);
            assertThatJson(response.body())
                    .inPath("$.error.data.requiredCapabilities.sampling")
                    .isEqualTo(Map.of());
        }
    }

    @Test
    void allowsCallWithDeclaredCapability() throws Exception {
        // language=JSON
        var body = """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "method": "tools/call",
                  "params": {
                    "name": "test_missing_capability",
                    "arguments": {},
                    "_meta": {
                      "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                      "io.modelcontextprotocol/clientInfo": {"name": "t", "version": "1"},
                      "io.modelcontextprotocol/clientCapabilities": {"sampling": {}}
                    }
                  }
                }
                """;
        var response = postMcpRequest(body, Map.of("Mcp-Method", "tools/call", "Mcp-Name", "test_missing_capability"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response).isSuccess().hasTextContent("sampling capability present");
    }
}
