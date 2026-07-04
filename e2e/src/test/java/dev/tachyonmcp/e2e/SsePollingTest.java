/*
 * Copyright (c) 2026 Konstantin Pavlov.
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * SSE polling behaviour: retry field, priming events, and event ID sequencing.
 *
 * <p>Pattern: POST to send requests, GET to receive SSE events.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SsePollingTest extends AbstractMcpE2eTest {

    @Test
    void getSseStreamIncludesRetryField() throws Exception {
        var sessionId = initializeSession();
        var raw = readRawSse(sessionId, 1024, 2000);
        assertThat(raw).contains("retry: 3000");
        assertThat(raw).contains("X-Accel-Buffering: no");
    }

    @Test
    void getSseStreamSendsPrimingEventWithEventId() throws Exception {
        var sessionId = initializeSession();
        var events = parseSseEvents(readRawSse(sessionId, 2048, 2000));

        assertThat(events).isNotEmpty();
        var first = events.getFirst();
        assertThat(first.id).as("Priming event should carry an event ID").isNotEmpty();
        assertThat(first.event).as("Priming event type").isEqualTo("message");
        assertThat(first.data).as("Priming event data should be empty").isEmpty();
    }

    @Test
    void getSseStreamPrimingHasSequentialId() throws Exception {
        var sessionId = initializeSession();

        var events1 = parseSseEvents(readRawSse(sessionId, 2048, 2000));
        assertThat(events1).isNotEmpty();
        var firstId = Long.parseLong(events1.getFirst().id());

        var events2 = parseSseEvents(readRawSse(sessionId, 2048, 2000));
        assertThat(events2).isNotEmpty();
        var secondId = Long.parseLong(events2.getFirst().id());

        assertThat(secondId).isGreaterThan(firstId);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String initializeSession() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var body = """
                    {"jsonrpc":"2.0","id":0,"method":"initialize",
                     "params":{"protocolVersion":"2025-11-25","capabilities":{},
                               "clientInfo":{"name":"test","version":"1.0"}}}
                    """;
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/mcp"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .header("MCP-Protocol-Version", "2025-11-25")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow();
            client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/mcp"))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json, text/event-stream")
                            .header("MCP-Protocol-Version", "2025-11-25")
                            .header("MCP-Session-Id", sessionId)
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                    {"jsonrpc":"2.0","method":"notifications/initialized"}
                                    """))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            return sessionId;
        }
    }

    /** Reads raw SSE bytes until {@code timeoutMs}. */
    private String readRawSse(String sessionId, int bufSize, int timeoutMs) throws Exception {
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
            var buf = new byte[bufSize];
            var deadline = System.currentTimeMillis() + timeoutMs;
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
            assertThat(raw).as("must have received data").isNotEmpty();
            return raw;
        }
    }

    private List<SseEvent> parseSseEvents(String raw) {
        return parseEvents(raw.lines().toList());
    }

    private List<SseEvent> parseEvents(List<String> lines) {
        var events = new ArrayList<SseEvent>();
        var bufId = new StringBuilder();
        var bufEvent = new StringBuilder();
        var bufData = new StringBuilder();

        for (var line : lines) {
            if (line.startsWith("retry: ") || line.isBlank()) {
                continue;
            }
            if (line.startsWith("id: ")) {
                if (!bufId.isEmpty()) {
                    events.add(new SseEvent(bufId.toString(), bufEvent.toString(), bufData.toString()));
                }
                bufId.setLength(0);
                bufEvent.setLength(0);
                bufData.setLength(0);
                bufId.append(line.substring("id: ".length()));
            } else if (line.startsWith("event: ")) {
                if (!bufEvent.isEmpty()) bufEvent.append('\n');
                bufEvent.append(line.substring("event: ".length()));
            } else if (line.startsWith("data: ")) {
                if (!bufData.isEmpty()) bufData.append('\n');
                bufData.append(line.substring("data: ".length()));
            }
        }
        if (!bufId.isEmpty()) {
            events.add(new SseEvent(bufId.toString(), bufEvent.toString(), bufData.toString()));
        }
        return List.copyOf(events);
    }

    record SseEvent(String id, String event, String data) {}
}
