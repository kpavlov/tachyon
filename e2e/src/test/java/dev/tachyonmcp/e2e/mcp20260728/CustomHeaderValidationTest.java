/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 {@code x-mcp-header}/{@code Mcp-Param-*} custom headers (SEP-2243): a tool
 * parameter annotated {@code x-mcp-header: "Region"} mirrors into an {@code Mcp-Param-Region} HTTP
 * header, which the server must validate against the body value (decoding the Base64 sentinel
 * format when present) and reject on mismatch/invalid-encoding/omission with {@code -32020}
 * (HeaderMismatch).
 */
class CustomHeaderValidationTest extends AbstractStatelessMcpE2eTest {

    // language=JSON
    private static final String EXECUTE_SQL_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "region": {"type": "string", "x-mcp-header": "Region"},
                "query": {"type": "string"}
              },
              "required": ["region", "query"]
            }
            """;

    @BeforeEach
    void registerFixtures() {
        startServer(b -> b.tool(ToolHandler.of(
                d -> d.name("execute_sql").description("Executes SQL").inputSchema(EXECUTE_SQL_SCHEMA),
                (ctx, request) ->
                        ToolResult.text("region=" + request.arguments().stringOr("region", "") + " query="
                                + request.arguments().stringOr("query", "")))));
    }

    private HttpResponse<String> post(String body, String regionHeader) throws Exception {
        var headers = new LinkedHashMap<String, String>();
        headers.put("Mcp-Method", "tools/call");
        headers.put("Mcp-Name", "execute_sql");
        if (regionHeader != null) headers.put("Mcp-Param-Region", regionHeader);
        return postMcpRequest(body, headers);
    }

    private String toolCallBody(int id, String region) {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": %d,
                  "method": "tools/call",
                  "params": {
                    "name": "execute_sql",
                    "arguments": {"region": "%s", "query": "SELECT 1"},
                    "_meta": {
                      "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                      "io.modelcontextprotocol/clientInfo": {"name": "t", "version": "1"},
                      "io.modelcontextprotocol/clientCapabilities": {}
                    }
                  }
                }
                """.formatted(id, region);
    }

    @Test
    void acceptsMatchingParamHeader() throws Exception {
        var response = post(toolCallBody(1, "us-west1"), "us-west1");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThatJson(response.body()).inPath("$.result.content[0].text").isEqualTo("region=us-west1 query=SELECT 1");
    }

    @Test
    void rejectsMissingParamHeaderWhenBodyHasValue() throws Exception {
        var response = post(toolCallBody(2, "us-west1"), null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThatJson(response.body()).inPath("$.id").isEqualTo(2);
        assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32020);
    }

    @Test
    void rejectsMismatchedParamHeader() throws Exception {
        var response = post(toolCallBody(3, "us-west1"), "eu-west1");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThatJson(response.body()).inPath("$.id").isEqualTo(3);
        assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32020);
    }

    @Test
    void acceptsBase64EncodedParamHeader() throws Exception {
        var encoded = java.util.Base64.getEncoder()
                .encodeToString("us-west1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = post(toolCallBody(4, "us-west1"), "=?base64?" + encoded + "?=");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThatJson(response.body()).inPath("$.result.content[0].text").isEqualTo("region=us-west1 query=SELECT 1");
    }

    @Test
    void rejectsInvalidBase64ParamHeader() throws Exception {
        var response = post(toolCallBody(5, "us-west1"), "=?base64?not-valid-base64!!?=");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThatJson(response.body()).inPath("$.id").isEqualTo(5);
        assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32020);
    }

    @Test
    void rejectsOverlappingBase64SentinelWithoutCrashing() throws Exception {
        // "=?base64?=" is short enough that the "=?base64?" prefix and "?=" suffix overlap by one
        // character; the server must not throw while extracting the (empty) encoded segment.
        var response = post(toolCallBody(7, "us-west1"), "=?base64?=");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThatJson(response.body()).inPath("$.id").isEqualTo(7);
        assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32020);
    }

    @Test
    void rejectsBase64ParamHeaderWithMissingPadding() throws Exception {
        // "Hello" -> "SGVsbG8=" — strip the trailing '=' pad character. Base64.getDecoder() tolerates
        // this (decodes fine without it), but SEP-2243 requires the server to reject malformed padding.
        var encoded = java.util.Base64.getEncoder()
                .encodeToString("Hello".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .replace("=", "");
        var response = post(toolCallBody(6, "Hello"), "=?base64?" + encoded + "?=");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThatJson(response.body()).inPath("$.id").isEqualTo(6);
        assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32020);
    }
}
