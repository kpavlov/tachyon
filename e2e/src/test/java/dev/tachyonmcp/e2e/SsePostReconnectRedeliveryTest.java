/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.OutboundSseStreamMessageRouter;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 Streamable HTTP resumability: a tool that closes its POST-SSE stream mid-call
 * before producing its result. The client observes the disconnect and resumes the stream with
 * {@code Last-Event-ID: <n>#<key>}; the server MUST still deliver the final response on that stream.
 *
 * <p>The tool sleeps after closing so the response is finalized (and appended to the event log)
 * only AFTER the client has already reconnected and run its one-shot replay — deterministically
 * exercising the reconnect-before-append race. Without live re-delivery on resume, the reconnecting
 * client would never receive the response.
 */
class SsePostReconnectRedeliveryTest extends AbstractMcpE2eTest {

    private static final Pattern PRIMING_ID = Pattern.compile("id: (\\d+#\\d+)", Pattern.MULTILINE);

    @Override
    protected void startDefaultServer() {
        startServer(it -> it.tool(selfClosingTool()));
    }

    @Test
    void reconnectAfterMidCallCloseReceivesResponse() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            // POST tools/call: the tool upgrades the POST to SSE, emits the priming event, then
            // closes the stream — so the POST completes carrying only that priming event.
            var post = client.post(sessionId, """
                    {"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"self-closing","arguments":{}}}
                    """);
            assertThat(post.headers().firstValue("Content-Type").orElse("")).contains("text/event-stream");
            var matcher = PRIMING_ID.matcher(post.body());
            assertThat(matcher.find())
                    .as("POST-SSE priming event id (<n>#<key>) in:\n%s", post.body())
                    .isTrue();
            var lastEventId = matcher.group(1);

            // Resume that exact POST stream. The tool is still sleeping, so its response has not
            // been appended yet: the one-shot replay finds nothing, and only live re-delivery on
            // resume can hand the client its result.
            try (var socket = openGetStream(sessionId, lastEventId)) {
                var received = readStream(socket, body -> body.contains("resumed-payload"), 5000);
                assertThat(received)
                        .as("resumed POST stream must receive the final response delivered after reconnect")
                        .contains("\"id\":9")
                        .contains("resumed-payload");
            }
        }
    }

    private Socket openGetStream(String sessionId, String lastEventId) throws IOException {
        var socket = new Socket("localhost", port);
        var req = "GET /mcp HTTP/1.1\r\n"
                + "Host: localhost:" + port + "\r\n"
                + "MCP-Session-Id: " + sessionId + "\r\n"
                + "MCP-Protocol-Version: 2025-11-25\r\n"
                + "Accept: text/event-stream\r\n"
                + "Last-Event-ID: " + lastEventId + "\r\n"
                + "\r\n";
        socket.getOutputStream().write(req.getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
        socket.setSoTimeout(50);
        return socket;
    }

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

    private static ToolHandler selfClosingTool() {
        return ToolHandler.of(
                b -> b.name("self-closing").description("Closes its SSE stream mid-call, then returns after a delay"),
                (ctx, args) -> {
                    var stream = OutboundSseStreamMessageRouter.currentOutboundSseStream();
                    if (stream != null) {
                        stream.start();
                        stream.close();
                    }
                    // Return only after the client has had time to observe the close and reconnect,
                    // so the response is appended after the resume's one-shot replay has run.
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return ToolResult.text("resumed-payload");
                });
    }
}
