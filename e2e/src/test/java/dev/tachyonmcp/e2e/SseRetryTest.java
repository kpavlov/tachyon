/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
class SseRetryTest extends AbstractMcpE2eTest {

    @Test
    void sseResponseIncludesRetryField() throws Exception {
        String sessionId;
        try (var client = HttpClient.newHttpClient()) {
            var initRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/mcp"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .header("MCP-Protocol-Version", "2025-11-25")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}
                            """))
                    .build();
            var initResponse = client.send(initRequest, HttpResponse.BodyHandlers.ofString());
            sessionId = initResponse.headers().firstValue("MCP-Session-Id").orElseThrow();
        }

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
            var buf = new byte[512];
            var deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    var n = socket.getInputStream().read(buf);
                    if (n < 0) break;
                    if (n > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                } catch (SocketTimeoutException e) {
                    // No data this poll; keep reading until the deadline.
                }
            }
            var raw = sb.toString();
            assertThat(raw).contains("retry: 3000");
            assertThat(raw).contains("X-Accel-Buffering: no");
        }
    }
}
