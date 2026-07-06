/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.Notifications;
import dev.tachyonmcp.protocol.Protocol;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class YouComSearchToolTest {
    @Test
    void fromEnvSkipsWhenKeyMissing() {
        assertThat(YouComSearchTool.fromEnv(Map.of())).isEmpty();
    }

    @Test
    void handlesSearchResults() throws Exception {
        try (var server = HttpServer.create(new InetSocketAddress(0), 0)) {
            server.createContext("/v1/search", exchange -> {
                assertThat(exchange.getRequestHeaders().getFirst("X-API-Key")).isEqualTo("test-key");
                var body = "{\"results\":{\"web\":[{\"title\":\"Example\",\"url\":\"https://example.com\",\"description\":\"Snippet\"}]}}";
                exchange.sendResponseHeaders(200, body.getBytes().length);
                try (var os = exchange.getResponseBody()) {
                    os.write(body.getBytes());
                }
            });
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();

            var tool = new YouComSearchTool(
                    "test-key",
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/search"),
                    HttpClient.newHttpClient());
            var result = tool.handle(noopContext(), new ToolArgs(Map.of("query", "playwright", "count", 3)));

            assertThat(result).isInstanceOf(ToolResult.Success.class);
            assertThat(result).isInstanceOfSatisfying(ToolResult.Success.class, success ->
                    assertThat(success.content().toString()).contains("Example", "https://example.com", "Snippet"));
        }
    }

    @Test
    void rejectsMissingQuery() {
        var tool = new YouComSearchTool("test-key", URI.create("http://localhost:1/v1/search"), HttpClient.newHttpClient());
        var result = tool.handle(noopContext(), new ToolArgs(Map.of()));
        assertThat(result).isInstanceOf(ToolResult.ErrorResult.class);
    }

    private static InteractionContext noopContext() {
        return new InteractionContext() {
            @Override public Protocol getProtocol() { return null; }
            @Override public Lifecycle getLifecycle() { return null; }
            @Override public Session session() { return null; }
            @Override public boolean isExtensionEnabled(String extensionId) { return false; }
            @Override public Notifications notifications() { return null; }
            @Override public CompletableFuture<String> sendRequest(String method, Object params) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public Map<String, Object> attributes() { return Map.of(); }
            @Override public void setAttribute(String name, Object value) {}
            @Override public <T> T getAttribute(String name) { return null; }
        };
    }
}
