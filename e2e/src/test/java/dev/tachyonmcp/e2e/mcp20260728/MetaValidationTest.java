/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 requires {@code _meta.io.modelcontextprotocol/protocolVersion},
 * {@code .../clientInfo}, and {@code .../clientCapabilities} on every request (SEP-2575). A request
 * missing any of them is malformed: the server must reject it with JSON-RPC {@code -32602}
 * (Invalid params) and HTTP {@code 400 Bad Request}, and the error response must still carry the
 * original request id.
 */
class MetaValidationTest extends AbstractStatelessMcpE2eTest {

    private static final String VALID_META = """
            "io.modelcontextprotocol/protocolVersion": "2026-07-28",
            "io.modelcontextprotocol/clientInfo": {"name": "t", "version": "1"},
            "io.modelcontextprotocol/clientCapabilities": {}
            """;

    private HttpResponse<String> postToolsCall(String metaJson) throws Exception {
        var body = """
                {
                  "jsonrpc": "2.0",
                  "id": 7,
                  "method": "tools/call",
                  "params": {
                    "name": "echo",
                    "arguments": {"message": "hi"}
                    %s
                  }
                }
                """.formatted(metaJson.isEmpty() ? "" : ",\"_meta\": {" + metaJson + "}");
        return postMcpRequest(body, Map.of("Mcp-Method", "tools/call", "Mcp-Name", "echo"));
    }

    private void assertRejected(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThatJson(response.body()).inPath("$.id").isEqualTo(7);
        assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32602);
    }

    @Test
    void rejectsMissingMeta() throws Exception {
        assertRejected(postToolsCall(""));
    }

    @Test
    void rejectsMissingProtocolVersion() throws Exception {
        assertRejected(postToolsCall("""
                "io.modelcontextprotocol/clientInfo": {"name": "t", "version": "1"},
                "io.modelcontextprotocol/clientCapabilities": {}
                """));
    }

    @Test
    void rejectsMissingClientInfo() throws Exception {
        assertRejected(postToolsCall("""
                "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                "io.modelcontextprotocol/clientCapabilities": {}
                """));
    }

    @Test
    void rejectsMissingClientCapabilities() throws Exception {
        assertRejected(postToolsCall("""
                "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                "io.modelcontextprotocol/clientInfo": {"name": "t", "version": "1"}
                """));
    }

    @Test
    void acceptsCompleteMeta() throws Exception {
        var response = postToolsCall(VALID_META);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThatJson(response.body()).inPath("$.result.content[0].text").isEqualTo("hi");
    }
}
