/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 Streamable HTTP, Resumability and Redelivery: "The server MUST NOT replay
 * messages that would have been sent on a different stream."
 *
 * <p>Scenario: the client's general-purpose GET stream disconnects; while it is down, a
 * tools/call upgrades its POST to an SSE stream and delivers a notification plus the JSON-RPC
 * response there — the client fully receives both. When the client resumes the GET stream with
 * its last GET-stream event ID, the replay must not re-deliver the POST-stream messages.
 */
class SseReplayPerStreamTest extends AbstractMcpE2eTest {

    private static final Pattern EVENT_ID = Pattern.compile("^id: (\\d+)", Pattern.MULTILINE);

    @Override
    protected void startDefaultServer() {
        startServer(it -> it.tool(notifyingEchoTool()));
    }

    @Test
    void getReconnectDoesNotReplayMessagesDeliveredOnPostStream() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            // GET stream #1: capture its priming event id as the client's Last-Event-ID
            // baseline, then drop the connection.
            long getStreamLastEventId;
            try (var socket = openGetStream(sessionId, null)) {
                var opening = readStream(socket, body -> EVENT_ID.matcher(body).find(), 5000);
                var matcher = EVENT_ID.matcher(opening);
                assertThat(matcher.find())
                        .as("GET stream priming event id in:\n%s", opening)
                        .isTrue();
                getStreamLastEventId = Long.parseLong(matcher.group(1));
            }

            // While the GET stream is down: tools/call → the inline notification upgrades the
            // POST to SSE, and both the notification and the response are DELIVERED there.
            var toolResponse = client.sendRequest(sessionId, """
                    {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"notifying-echo","arguments":{"message":"post-stream-payload"}}}
                    """);
            assertThat(toolResponse.headers().firstValue("Content-Type").orElse(""))
                    .contains("text/event-stream");
            assertThat(toolResponse.body()).contains("notifications/tool/echoed");
            assertThat(toolResponse.body()).contains("post-stream-payload");
            assertThat(toolResponse.body()).contains("\"result\"");

            // Resume the GET stream from the pre-call baseline. Per spec the server may replay
            // only messages of the disconnected (GET) stream — never the POST stream's.
            try (var socket = openGetStream(sessionId, getStreamLastEventId)) {
                // Read until the new priming event arrives, then linger so an (incorrect)
                // replay of the POST-stream messages would have time to show up.
                var replayed = readStream(socket, body -> EVENT_ID.matcher(body).find(), 5000);
                replayed = replayed + readStream(socket, body -> false, 1000);

                assertThat(replayed)
                        .as("GET resume must not replay the response delivered on the POST stream")
                        .doesNotContain("\"id\":7")
                        .doesNotContain("\"result\"");
                assertThat(replayed)
                        .as("GET resume must not replay notifications delivered on the POST stream")
                        .doesNotContain("notifications/tool/echoed")
                        .doesNotContain("post-stream-payload");
            }
        }
    }

    private Socket openGetStream(String sessionId, Long lastEventId) throws IOException {
        var socket = new Socket("localhost", port);
        var req = new StringBuilder("GET /mcp HTTP/1.1\r\n")
                .append("Host: localhost:")
                .append(port)
                .append("\r\n")
                .append("MCP-Session-Id: ")
                .append(sessionId)
                .append("\r\n")
                .append("MCP-Protocol-Version: 2025-11-25\r\n")
                .append("Accept: text/event-stream\r\n");
        if (lastEventId != null) {
            req.append("Last-Event-ID: ").append(lastEventId).append("\r\n");
        }
        req.append("\r\n");
        socket.getOutputStream().write(req.toString().getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
        socket.setSoTimeout(50);
        return socket;
    }

    /** Reads the stream until {@code until} is satisfied or {@code timeoutMs} elapses. */
    private static String readStream(Socket socket, java.util.function.Predicate<String> until, long timeoutMs)
            throws IOException {
        var sb = new StringBuilder();
        var buf = new byte[1024];
        var deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && !until.test(sb.toString())) {
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

    private static ToolHandler notifyingEchoTool() {
        return ToolHandler.of(
                b -> b.name("notifying-echo")
                        .description("Echoes the message, emitting a notification so the POST upgrades to SSE"),
                (context, args) -> {
                    String text = "";
                    var msg = args.raw("message");
                    if (msg instanceof tools.jackson.databind.JsonNode node) {
                        text = node.asString();
                    }
                    context.notifications().send("notifications/tool/echoed", Map.of("message", text));
                    return ToolResult.text(text);
                });
    }
}
