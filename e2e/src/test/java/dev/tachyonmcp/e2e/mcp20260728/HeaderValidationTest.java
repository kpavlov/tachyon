/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 mirrors {@code method}/{@code params.name} into the {@code Mcp-Method}/
 * {@code Mcp-Name} HTTP headers (SEP-2243). A server processing the body must reject a request
 * where the header doesn't match with JSON-RPC {@code -32020} (HeaderMismatch) and HTTP
 * {@code 400 Bad Request}.
 */
class HeaderValidationTest extends AbstractStatelessMcpE2eTest {

    // language=JSON
    private static final String TOOLS_CALL_ECHO_BODY = """
            {
              "jsonrpc": "2.0",
              "id": 9,
              "method": "tools/call",
              "params": {
                "name": "echo",
                "arguments": {"message": "hi"},
                "_meta": {
                  "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                  "io.modelcontextprotocol/clientInfo": {"name": "t", "version": "1"},
                  "io.modelcontextprotocol/clientCapabilities": {}
                }
              }
            }
            """;

    private HttpResponse<String> post(String mcpMethodHeader, String mcpNameHeader) throws Exception {
        var headers = new LinkedHashMap<String, String>();
        if (mcpMethodHeader != null) headers.put("Mcp-Method", mcpMethodHeader);
        if (mcpNameHeader != null) headers.put("Mcp-Name", mcpNameHeader);
        return postMcpRequest(TOOLS_CALL_ECHO_BODY, headers);
    }

    private void assertHeaderMismatch(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThatJson(response.body()).inPath("$.id").isEqualTo(9);
        assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32020);
    }

    @Test
    void rejectsMismatchedNameHeader() throws Exception {
        assertHeaderMismatch(post("tools/call", "not_echo"));
    }

    @Test
    void rejectsMissingNameHeaderWhenBodyHasName() throws Exception {
        assertHeaderMismatch(post("tools/call", null));
    }

    @Test
    void rejectsMismatchedMethodHeader() throws Exception {
        assertHeaderMismatch(post("tools/list", "echo"));
    }

    @Test
    void acceptsWhitespacePaddedNameHeader() throws Exception {
        var response = post("tools/call", "  echo  ");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThatJson(response.body()).inPath("$.result.content[0].text").isEqualTo("hi");
    }

    @Test
    void acceptsBase64EncodedNameHeader() throws Exception {
        var encoded = Base64.getEncoder().encodeToString("echo".getBytes(StandardCharsets.UTF_8));
        var response = post("tools/call", "=?base64?" + encoded + "?=");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThatJson(response.body()).inPath("$.result.content[0].text").isEqualTo("hi");
    }

    @Test
    void rejectsProtocolVersionHeaderMismatchingMeta() throws Exception {
        // language=JSON
        var body = """
                {
                  "jsonrpc": "2.0",
                  "id": 11,
                  "method": "tools/call",
                  "params": {
                    "name": "echo",
                    "arguments": {"message": "hi"},
                    "_meta": {
                      "io.modelcontextprotocol/protocolVersion": "2099-01-01",
                      "io.modelcontextprotocol/clientInfo": {"name": "t", "version": "1"},
                      "io.modelcontextprotocol/clientCapabilities": {}
                    }
                  }
                }
                """;
        var response = postMcpRequest(body, Map.of("Mcp-Method", "tools/call", "Mcp-Name", "echo"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThatJson(response.body()).inPath("$.id").isEqualTo(11);
        assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32020);
    }
}
