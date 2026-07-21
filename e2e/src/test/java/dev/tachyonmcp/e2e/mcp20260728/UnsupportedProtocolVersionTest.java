/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * SEP-2575: a request naming a protocol version the server does not implement (unknown, or a
 * known version the server declined to support) must be rejected with an
 * UnsupportedProtocolVersionError whose {@code error.data} lists the versions the server does
 * support and echoes the requested one, over HTTP {@code 400 Bad Request}, with the response
 * {@code id} matching the request's JSON-RPC id.
 */
class UnsupportedProtocolVersionTest extends AbstractStatelessMcpE2eTest {

    @Test
    void rejectsUnsupportedProtocolVersion() throws Exception {
        // language=JSON
        var body = """
                {
                  "jsonrpc": "2.0",
                  "id": 301,
                  "method": "server/discover",
                  "params": {
                    "_meta": {
                      "io.modelcontextprotocol/protocolVersion": "v999.0.0",
                      "io.modelcontextprotocol/clientInfo": {"name": "t", "version": "1"},
                      "io.modelcontextprotocol/clientCapabilities": {}
                    }
                  }
                }
                """;
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Origin", "http://localhost:3000")
                .header("MCP-Protocol-Version", "v999.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32022);
        assertThatJson(response.body()).inPath("$.error.data.requested").isEqualTo("v999.0.0");
        assertThatJson(response.body())
                .inPath("$.error.data.supported")
                .isArray()
                .contains("2026-07-28", "2025-11-25");
        assertThatJson(response.body()).inPath("$.id").isEqualTo(301);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:3000");
    }

    @Test
    void rejectsUnsupportedProtocolVersionWithUnparsableBodyUsingNullId() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "v999.0.0")
                .POST(HttpRequest.BodyPublishers.ofString("not json"))
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("\"id\":null");
    }
}
