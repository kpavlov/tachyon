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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MCP 2026-07-28 removed {@code initialize}, {@code ping}, {@code logging/setLevel},
 * {@code resources/subscribe}, and {@code resources/unsubscribe} (SEP-2575): a server implementing
 * only this revision must answer them with HTTP 404 and JSON-RPC {@code -32601} (Method not found),
 * not dispatch them as it would under 2025-11-25.
 */
class RemovedMethodsTest extends AbstractStatelessMcpE2eTest {

    @ParameterizedTest
    @ValueSource(strings = {"initialize", "ping", "logging/setLevel", "resources/subscribe", "resources/unsubscribe"})
    void removedMethodReturns404MethodNotFound(String method) throws Exception {
        var body = "{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"%s\",\"params\":{}}".formatted(method);
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2026-07-28")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as("status for removed method '%s': %s", method, response.body())
                .isEqualTo(404);
        assertThatJson(response.body()).inPath("$.id").isEqualTo(42);
        assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32601);
    }
}
