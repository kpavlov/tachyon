/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.McpServerHandle;
import dev.tachyonmcp.server.TachyonMcpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatelessServerTest {

    private McpServerHandle serverHandle;
    private int port;

    @BeforeAll
    void beforeAll() {
        serverHandle = TachyonMcpServer.builder()
                .tool(new EchoToolHandler())
                .stateless(true)
                .host("localhost")
                .port(0)
                .bind();
        port = serverHandle.port();
    }

    @AfterAll
    void afterAll() {
        serverHandle.close();
    }

    @Test
    void shouldInitializeWithoutIssuingSessionId() throws Exception {
        try (var client = new TestMcpClient(port)) {

            var response = client.post(
                    // language=JSON
                    """
                    {"jsonrpc":"2.0","id":1,"method":"initialize",
                     "params":{"protocolVersion":"2025-11-25","capabilities":{},
                               "clientInfo":{"name":"test","version":"1.0"}}}
                    """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.body()).inPath("$.result.protocolVersion").isEqualTo("2025-11-25");
            assertThat(response.headers().firstValue("MCP-Session-Id")).isEmpty();
        }
    }

    @Test
    void shouldDispatchToolsListWithoutSessionId() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = post(client, null, """
                    {"jsonrpc":"2.0","id":1,"method":"tools/list"}
                    """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.body()).inPath("$.result.tools").isArray().isNotEmpty();
        }
    }

    @Test
    void shouldExecuteToolCallWithoutSessionId() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = post(client, null, """
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"echo","arguments":{"message":"hello"}}}
                    """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.body()).inPath("$.result.content[0].text").isEqualTo("hello");
        }
    }

    @Test
    void shouldAcceptNotificationWithoutSessionId() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = post(client, null, """
                    {"jsonrpc":"2.0","method":"notifications/initialized"}
                    """);

            assertThat(response.statusCode()).isEqualTo(202);
        }
    }

    @ParameterizedTest(name = "POST method={0}")
    @ValueSource(strings = {"tools/list", "tools/call", "ping"})
    void shouldReturn404WhenPostCarriesSessionId(String method) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = post(client, "sess_12345678", """
                    {"jsonrpc":"2.0","id":1,"method":"%s"}
                    """.formatted(method));

            assertThat(response.statusCode()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn404WhenGetCarriesSessionId() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/mcp"))
                    .header("Accept", "text/event-stream")
                    .header("MCP-Protocol-Version", "2025-11-25")
                    .header("MCP-Session-Id", "sess_12345678")
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn405ForDeleteInStatelessMode() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/mcp"))
                    .header("MCP-Protocol-Version", "2025-11-25")
                    .DELETE()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(405);
        }
    }

    @Test
    void shouldOpenSseStreamWithoutSessionId() throws Exception {
        try (var client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/mcp"))
                    .header("Accept", "text/event-stream")
                    .header("MCP-Protocol-Version", "2025-11-25")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofLines());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type")).hasValue("text/event-stream");
            response.body().close();
        }
    }

    private HttpResponse<String> post(HttpClient client, @Nullable String sessionId, String body) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-11-25")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            builder.header("MCP-Session-Id", sessionId);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
