/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.transport.netty.McpChannelInitializer;
import dev.tachyonmcp.transport.netty.NettyIoEngine;
import dev.tachyonmcp.transport.netty.NettyServer;
import dev.tachyonmcp.transport.netty.NettyServerConfig;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Verifies that a silent SSE stream is kept alive by periodic scheduler-driven comment heartbeats
 * rather than being reaped on idle. Uses a short heartbeat interval so beats arrive within the
 * test window, proving the scheduler runs independently of the reader-idle timeout.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SseHeartbeatTest {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMillis(150);

    private Server server;
    private NettyServer nettyServer;
    private int port;

    @BeforeAll
    void startServer() {
        server = TachyonServer.builder()
                .session(s -> s.enabled(true))
                .network(n -> n.heartbeatInterval(HEARTBEAT_INTERVAL)
                        // Keep reader-idle longer than the test window so idle never fires
                        .readerIdleTimeout(Duration.ofMinutes(5)))
                .build();
        var config = new NettyServerConfig(
                "127.0.0.1",
                0,
                "/mcp",
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                McpChannelInitializer.DEFAULT_MAX_CONTENT_LENGTH,
                NettyServerConfig.buildCorsConfig(null, false, false, null),
                NettyIoEngine.AUTO,
                null);
        nettyServer = new NettyServer(server, config);
        port = nettyServer.port();
    }

    @AfterAll
    void stopServer() {
        nettyServer.close();
        server.close();
    }

    @Test
    void idleSseStreamReceivesRepeatedHeartbeats() throws Exception {
        var sessionId = initializeSession();

        // Read for ~5 heartbeat intervals; a 150ms interval should yield several ":\r\n" comments.
        var raw = readRawSse(sessionId, HEARTBEAT_INTERVAL.toMillis() * 5);

        assertThat(raw).as("stream must open as text/event-stream").contains("text/event-stream");
        assertThat(raw).contains("X-Accel-Buffering: no");
        assertThat(countOccurrences(raw, ":\r\n"))
                .as("scheduler must drive repeated SSE comment heartbeats")
                .isGreaterThanOrEqualTo(2);
    }

    private String initializeSession() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            // language=JSON
            var body = """
                {"jsonrpc":"2.0","id":0,"method":"initialize",
                 "params":{"protocolVersion":"2025-11-25","capabilities":{},
                           "clientInfo":{"name":"test","version":"1.0"}}}
                """;
            var response = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/mcp"))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json, text/event-stream")
                            .header("MCP-Protocol-Version", "2025-11-25")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            var sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow();
            client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/mcp"))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json, text/event-stream")
                            .header("MCP-Protocol-Version", "2025-11-25")
                            .header("MCP-Session-Id", sessionId)
                            // language=JSON
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            return sessionId;
        }
    }

    /**
     * Opens a raw SSE GET and accumulates everything received over {@code durationMs}.
     */
    private String readRawSse(String sessionId, long durationMs) throws Exception {
        try (var socket = new Socket("localhost", port)) {
            var req = ("GET /mcp HTTP/1.1\r\n"
                            + "Host: localhost:" + port + "\r\n"
                            + "MCP-Session-Id: " + sessionId + "\r\n"
                            + "Accept: text/event-stream\r\n"
                            + "\r\n")
                    .getBytes(StandardCharsets.UTF_8);
            socket.getOutputStream().write(req);
            socket.getOutputStream().flush();
            socket.setSoTimeout(50);

            var sb = new StringBuilder();
            var buf = new byte[4096];
            var deadline = System.currentTimeMillis() + durationMs;
            while (System.currentTimeMillis() < deadline) {
                try {
                    var n = socket.getInputStream().read(buf);
                    if (n < 0) break;
                    if (n > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                } catch (SocketTimeoutException e) {
                    // No data this poll; keep reading until the deadline.
                }
            }
            return sb.toString();
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        var count = 0;
        for (var i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
