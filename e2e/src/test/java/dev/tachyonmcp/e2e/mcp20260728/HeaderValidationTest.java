/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp20260728;

import static dev.tachyonmcp.testkit.McpHttpResponseAssert.assertThatResponse;

import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static final String VERSION_REJECTION = "SEP-2243 headers require MCP-Protocol-Version: 2026-07-28";

    private HttpResponse<String> post(String mcpMethodHeader, String mcpNameHeader) throws Exception {
        var headers = new LinkedHashMap<String, String>();
        if (mcpMethodHeader != null) headers.put("Mcp-Method", mcpMethodHeader);
        if (mcpNameHeader != null) headers.put("Mcp-Name", mcpNameHeader);
        return postMcpRequest(TOOLS_CALL_ECHO_BODY, headers);
    }

    private void assertHeaderMismatch(HttpResponse<String> response) {
        assertThatResponse(response).hasStatus(400).isJsonRpcError().hasId(9).hasErrorCode(-32020);
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
        assertThatResponse(response).hasStatus(200).isSuccess().hasTextContent("hi");
    }

    @Test
    void acceptsBase64EncodedNameHeader() throws Exception {
        var encoded = Base64.getEncoder().encodeToString("echo".getBytes(StandardCharsets.UTF_8));
        var response = post("tools/call", "=?base64?" + encoded + "?=");
        assertThatResponse(response).hasStatus(200).isSuccess().hasTextContent("hi");
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

        assertThatResponse(response).hasStatus(400).isJsonRpcError().hasId(11).hasErrorCode(-32020);
    }

    /** Rejected pre-aggregation, so the response is a plain {@code 400}, not JSON-RPC {@code -32020}. */
    private void assertDuplicateHeaderRejected(HttpResponse<String> response) {
        assertThatResponse(response).isRejectedWith(400, "Duplicate MCP header");
    }

    @Test
    void rejectsDuplicateMethodHeaders() throws Exception {
        assertDuplicateHeaderRejected(postMcpRequest(
                TOOLS_CALL_ECHO_BODY,
                Map.of("Mcp-Method", List.of("tools/call", "tools/list"), "Mcp-Name", List.of("echo")),
                true));
    }

    @Test
    void rejectsDuplicateNameHeaders() throws Exception {
        assertDuplicateHeaderRejected(postMcpRequest(
                TOOLS_CALL_ECHO_BODY,
                Map.of("Mcp-Method", List.of("tools/call"), "Mcp-Name", List.of("echo", "other_tool")),
                true));
    }

    @Test
    void rejectsDuplicateIdenticalNameHeaders() throws Exception {
        assertDuplicateHeaderRejected(postMcpRequest(
                TOOLS_CALL_ECHO_BODY,
                Map.of("Mcp-Method", List.of("tools/call"), "Mcp-Name", List.of("echo", "echo")),
                true));
    }

    /** Duplicates here are a downgrade: first value negotiates, an intermediary reads the last. */
    @Test
    void rejectsDuplicateProtocolVersionHeaders() throws Exception {
        assertDuplicateHeaderRejected(postMcpRequest(
                TOOLS_CALL_ECHO_BODY,
                Map.of(
                        "MCP-Protocol-Version", List.of("2025-11-25", "2026-07-28"),
                        "Mcp-Method", List.of("tools/call"),
                        "Mcp-Name", List.of("echo")),
                false));
    }

    /**
     * Header/body agreement is enforced only by this revision's {@code RequestValidationHandler}. A
     * mirrored header on a request negotiating an older revision is therefore never compared to the
     * body: a gateway would authorize the benign {@code Mcp-Method} while the body ran something
     * else. Rejecting costs nothing — the mirrors did not exist before 2026-07-28.
     */
    @Test
    void rejectsMirroredHeadersOnOlderProtocolVersion() throws Exception {
        var response = postMcpRequest(
                TOOLS_CALL_ECHO_BODY,
                Map.of(
                        "MCP-Protocol-Version", List.of("2025-11-25"),
                        "Mcp-Method", List.of("tools/list"),
                        "Mcp-Name", List.of("echo")),
                false);

        assertThatResponse(response).isRejectedWith(400, VERSION_REJECTION);
    }

    @Test
    void rejectsMirroredHeadersWithNoProtocolVersion() throws Exception {
        var response = postMcpRequest(
                TOOLS_CALL_ECHO_BODY, Map.of("Mcp-Method", List.of("tools/list"), "Mcp-Name", List.of("echo")), false);

        assertThatResponse(response).isRejectedWith(400, VERSION_REJECTION);
    }
}
