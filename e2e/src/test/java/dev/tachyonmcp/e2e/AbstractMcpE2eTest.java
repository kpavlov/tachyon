/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.ServerBuilder;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.transport.netty.NettyServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractMcpE2eTest {

    protected TachyonServer server;
    protected NettyServer nettyServer;
    protected int port;
    private boolean usingCustomServer;

    /** Exposes the internal engine SPI for tests that need server introspection. */
    protected ServerEngine engine() {
        return (ServerEngine) server;
    }

    @BeforeAll
    void beforeAll() {
        startDefaultServer();
    }

    @AfterAll
    void tearDown() {
        if (usingCustomServer) {
            nettyServer.close();
            server.close();
            nettyServer = null;
            server = null;
            usingCustomServer = false;
        }
    }

    protected TestMcpClient createTestClient() {
        return new TestMcpClient(port);
    }

    // region: ---- McpServer lifecycle management (call from subclass setup / teardown) ----

    protected void startDefaultServer() {
        var h = SharedE2eServer.ensureStarted();
        this.server = (ServerEngine) h;
        this.port = h.port();
        this.usingCustomServer = false;
    }

    protected void startEmptyServer() {
        startServer((b) -> {});
    }

    protected void startServer(Consumer<ServerBuilder> configurer) {
        if (usingCustomServer) {
            nettyServer.close();
            server.close();
        }
        ServerBuilder serverBuilder = TachyonServer.builder();
        serverBuilder.session(s -> s.enabled(true));
        configurer.accept(serverBuilder);
        this.server = serverBuilder.build();
        this.nettyServer = new NettyServer(0, (ServerEngine) server);
        this.port = nettyServer.port();
        this.usingCustomServer = true;
    }

    protected void stopServer() {
        if (usingCustomServer) {
            nettyServer.close();
            server.close();
            nettyServer = null;
            server = null;
            usingCustomServer = false;
        }
    }

    // endregion

    // region: ---- JSON-RPC helpers ----

    /**
     * Sends a JSON-RPC request and returns the response body, extracting a JSON-RPC envelope from
     * an SSE body if the response content-type is {@code text/event-stream}.
     */
    protected static String rpc(HttpClient client, int port, String sessionId, @Language("json") String body)
            throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-11-25")
                .header("MCP-Session-Id", sessionId)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var contentType = response.headers().firstValue("content-type").orElse("");
        if (contentType.startsWith("text/event-stream")) {
            return extractJsonRpcResponse(response.body(), extractRequestId(body));
        }
        return response.body();
    }

    /** Parses {@code "id":<n>} or {@code "id":"<s>"} out of a JSON-RPC request body. */
    protected static String extractRequestId(String requestBody) {
        var idx = requestBody.indexOf("\"id\"");
        if (idx < 0) return "";
        var colon = requestBody.indexOf(':', idx);
        if (colon < 0) return "";
        var sb = new StringBuilder();
        for (int i = colon + 1; i < requestBody.length(); i++) {
            var c = requestBody.charAt(i);
            if (c == ',' || c == '}') break;
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Walks all {@code data:} lines in an SSE body and returns the JSON payload whose JSON-RPC
     * envelope has an id matching {@code requestId}. Falls back to the last data line if no match.
     */
    protected static String extractJsonRpcResponse(String sseBody, String requestId) {
        String last = null;
        var idMarker = "\"id\":" + requestId;
        for (var line : sseBody.split("\n")) {
            String data = null;
            if (line.startsWith("data: ")) data = line.substring("data: ".length());
            else if (line.startsWith("data:")) data = line.substring("data:".length());
            if (data == null) continue;
            last = data;
            if (!requestId.isEmpty() && data.contains(idMarker)) {
                return data;
            }
        }
        assertThat(last).as("SSE response must contain at least one data line").isNotNull();
        return last;
    }

    // endregion
}
