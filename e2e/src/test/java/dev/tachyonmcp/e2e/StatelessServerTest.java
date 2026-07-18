/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.TachyonServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatelessServerTest {

    private final TachyonServer tachyonServer = TachyonServer.builder()
            .tool(EchoToolHandler.create())
            .network(n -> n.host("localhost").port(0))
            .start();
    private int port;

    @BeforeAll
    void beforeAll() {
        port = tachyonServer.port();
    }

    @AfterAll
    void afterAll() {
        tachyonServer.close();
    }

    @Test
    void shouldCompleteLifecycleAndDispatchWithoutSessionId() throws Exception {
        try (var client = new TestMcpClient(port)) {
            // MCP 2025-11-25 lifecycle: initialize first, then notify initialized.
            var sessionId = client.initialize();

            var response = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                    """);

            assertThat(sessionId).isNull();
            // language=JSON
            var expected = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "result": {
                        "tools": [{
                          "name": "echo",
                          "description": "Echo back the input message",
                          "inputSchema": {
                            "type": "object",
                            "properties": {
                              "message": {
                                "type": "string",
                                "description": "Message to echo"
                              }
                            },
                            "required": ["message"]
                          }
                        }]
                      }
                    }
                    """;
            assertThatJson(response).when(Option.IGNORING_EXTRA_FIELDS).isEqualTo(expected);
        }
    }

    @Test
    void shouldExecuteToolCallWithoutSessionId() throws Exception {
        try (var client = new TestMcpClient(port)) {
            client.initialize();

            var response = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"tools/call",
                     "params":{"name":"echo","arguments":{"message":"hello"}}}
                    """);

            // language=JSON
            var expected = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "result": {
                        "content": [{"type": "text", "text": "hello"}]
                      }
                    }
                    """;
            assertThatJson(response).isEqualTo(expected);
        }
    }

    @Test
    void shouldAcceptNotificationWithoutSessionId() throws Exception {
        try (var client = new TestMcpClient(port)) {
            var response = client.post(null, """
                    {"jsonrpc":"2.0","method":"notifications/initialized"}
                    """);

            assertThat(response.statusCode()).isEqualTo(202);
        }
    }

    @ParameterizedTest(name = "POST method={0}")
    @ValueSource(strings = {"tools/list", "tools/call", "ping"})
    void shouldReturn404WhenPostCarriesSessionId(String method) throws Exception {
        try (var client = new TestMcpClient(port)) {
            var response = client.post("sess_12345678", """
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
}
