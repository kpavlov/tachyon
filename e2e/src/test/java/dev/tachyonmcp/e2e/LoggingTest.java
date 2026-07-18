/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class LoggingTest extends AbstractStatefulMcpE2eTest {

    @Test
    void shouldReceiveLoggingNotificationAfterToolCall() throws Exception {
        try (var client = createTestClient()) {

            var sessionId = client.initialize();

            var setLevelBody =
                    // language=JSON
                    """
                    {"jsonrpc":"2.0","id":2,"method":"logging/setLevel","params":{"level":"debug"}}
                    """;
            var setLevelResponse = client.post(sessionId, setLevelBody);
            assertThatJson(setLevelResponse.body()).inPath("$.result").isObject();

            var toolBody =
                    // language=JSON
                    """
                    {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello"}}}
                    """;
            var toolResponse = client.post(sessionId, toolBody);

            assertThat(toolResponse.body())
                    .contains(
                            // language=json
                            """
                            {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"hello"}]}}
                            """.trim());
        }
    }

    @Test
    @Timeout(30)
    void shouldBroadcastServerLogToListeningClient() throws Exception {
        // server.notifications().log(...) fans out to every active session, delivered on its GET listen stream
        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            client.post(
                    sessionId,
                    // language=JSON
                    """
                    {"jsonrpc":"2.0","id":2,"method":"logging/setLevel","params":{"level":"debug"}}
                    """);

            var events = readListenStreamWhileBroadcasting(sessionId).toList();

            assertThat(events)
                    .as("the server-scoped error broadcast must arrive on the client's listen stream")
                    .anyMatch(data -> data.contains("notifications/message")
                            && data.contains("\"level\":\"error\"")
                            && data.contains("\"logger\":\"tachyon.svc\"")
                            && data.contains("server-broadcast"));
        }
    }

    /**
     * Opens a raw GET SSE listen stream and, once it is primed, triggers a server-scoped broadcast so
     * it rides that stream. Returns each SSE event's {@code data:} payload as a stream, read until the
     * broadcast is observed (or the read window elapses).
     */
    private Stream<String> readListenStreamWhileBroadcasting(String sessionId) throws Exception {
        try (var socket = new Socket("localhost", port)) {
            var req = ("GET /mcp HTTP/1.1\r\n"
                            + "Host: localhost:" + port + "\r\n"
                            + "MCP-Session-Id: " + sessionId + "\r\n"
                            + "MCP-Protocol-Version: 2025-11-25\r\n"
                            + "Accept: text/event-stream\r\n"
                            + "\r\n")
                    .getBytes(StandardCharsets.UTF_8);
            socket.getOutputStream().write(req);
            socket.getOutputStream().flush();
            socket.setSoTimeout(100);

            var sb = new StringBuilder();
            var buf = new byte[2048];
            var broadcast = false;
            var deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    var n = socket.getInputStream().read(buf);
                    if (n < 0) break;
                    sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                } catch (SocketTimeoutException ignored) {
                    // idle tick — fall through to broadcast/exit checks
                }
                if (!broadcast && sb.indexOf("id:") >= 0) {
                    // stream is primed; fire a server-scoped broadcast that must ride this stream
                    server.notifications().error("tachyon.svc", Map.of("event", "server-broadcast"));
                    broadcast = true;
                }
                if (sb.indexOf("server-broadcast") >= 0) break;
            }
            // The stream is materialized over the accumulated text, so it outlives the closed socket.
            return sb.toString()
                    .lines()
                    .filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring("data:".length()).trim())
                    .filter(data -> !data.isEmpty());
        }
    }
}
