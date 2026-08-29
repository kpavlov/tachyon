/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp.v2025_11_25;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.core.server.TachyonServer;
import dev.tachyonmcp.e2e.mcp.EchoToolHandler;
import dev.tachyonmcp.testkit.Mcp20251125Client;
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

    private final TachyonServer tachyonServer = createServer();
    private int port;

    private static TachyonServer createServer() {
        var server = TachyonServer.builder()
                .network(n -> n.host("localhost").port(0))
                .build();
        server.tools().registerAsync(EchoToolHandler.DESCRIPTOR, EchoToolHandler.FN);
        server.start();
        return server;
    }

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
        try (var client = new Mcp20251125Client(port)) {
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
            assertThatJson(response.body()).when(Option.IGNORING_EXTRA_FIELDS).isEqualTo(expected);
        }
    }

    @Test
    void shouldExecuteToolCallWithoutSessionId() throws Exception {
        try (var client = new Mcp20251125Client(port)) {
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
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }

    @Test
    void shouldAcceptNotificationWithoutSessionId() throws Exception {
        try (var client = new Mcp20251125Client(port)) {
            var response = client.post(null, """
                    {"jsonrpc":"2.0","method":"notifications/initialized"}
                    """);

            assertThat(response.statusCode()).isEqualTo(202);
        }
    }

    @ParameterizedTest(name = "POST method={0}")
    @ValueSource(strings = {"tools/list", "tools/call", "ping"})
    void shouldReturn404WhenPostCarriesSessionId(String method) throws Exception {
        try (var client = new Mcp20251125Client(port)) {
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
