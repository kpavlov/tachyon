/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import java.time.Duration;

import dev.tachyonmcp.e2e.mcp.v2025_11_25.AbstractStatefulMcpE2eTest;
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
class SseReplayPerStreamTest extends AbstractStatefulMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        var descriptor = ToolDescriptor.builder()
                .name("notifying-echo")
                .description("Echoes the message, emitting a notification so the POST upgrades to SSE")
                .build();
        startServerWith(s -> s.tools().register(descriptor, (context, request) -> {
            var args = request.arguments();
            var text = args.stringOr("message", "");
            context.notifications().progress(request.progressToken(), 1, 1, text);
            return ToolResult.text(text);
        }));
    }

    @Test
    void getReconnectDoesNotReplayMessagesDeliveredOnPostStream() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            // GET stream #1: capture its priming event id as the client's Last-Event-ID
            // baseline, then drop the connection.
            String getStreamLastEventId;
            try (var subscriber = client.openGetStream(null)) {
                getStreamLastEventId = subscriber.awaitFirstEventId(Duration.ofSeconds(5));
            }

            // While the GET stream is down: tools/call → the inline notification upgrades the
            // POST to SSE, and both the notification and the response are DELIVERED there.
            var toolResponse = client.post(sessionId, """
                    {"jsonrpc":"2.0","id":7,"method":"tools/call",
                     "params":{"name":"notifying-echo","arguments":{"message":"post-stream-payload"},
                               "_meta":{"progressToken":"pt-replay"}}}
                    """);
            assertThat(toolResponse.headers().firstValue("Content-Type").orElse(""))
                    .contains("text/event-stream");
            assertThat(toolResponse.body()).contains("notifications/progress");
            assertThat(toolResponse.body()).contains("post-stream-payload");
            assertThat(toolResponse.body()).contains("\"result\"");

            // Resume the GET stream from the pre-call baseline. Per spec the server may replay
            // only messages of the disconnected (GET) stream — never the POST stream's.
            try (var subscriber = client.openGetStream(getStreamLastEventId)) {
                // Wait for the new priming event, then linger so an (incorrect) replay of the
                // POST-stream messages would have time to show up — there's no faster way to
                // confirm an absence than waiting out the window.
                subscriber.awaitFirstEventId(Duration.ofSeconds(5));
                Thread.sleep(1000);

                var replayed = subscriber.rawResponse();
                assertThat(replayed)
                        .as("GET resume must not replay the response delivered on the POST stream")
                        .doesNotContain("\"id\":7")
                        .doesNotContain("\"result\"");
                assertThat(replayed)
                        .as("GET resume must not replay notifications delivered on the POST stream")
                        .doesNotContain("notifications/progress")
                        .doesNotContain("post-stream-payload");
            }
        }
    }
}
