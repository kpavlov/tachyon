/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.transport.netty.McpChannelInitializer;
import dev.tachyonmcp.transport.netty.NettyIoEngine;
import dev.tachyonmcp.transport.netty.NettyServer;
import dev.tachyonmcp.transport.netty.NettyServerConfig;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Verifies the lazy SSE upgrade and keep-alive path exercised when a tool emits
 * {@code notifications/progress}: the first progress event upgrades the POST response to
 * {@code text/event-stream}, and periodic reader-idle ticks emit {@code :\r\n} heartbeats rather
 * than closing the channel.
 *
 * <p>The SSE upgrade is asynchronous — the tool runs off the event loop and {@code SseHeartbeat} is
 * only armed once its first {@code progress()} reaches {@code PostSseStream.doStart}. A cold JVM can
 * lose the race against the reader-idle timer, so {@link #warmUp()} JIT-warms the whole dispatch
 * path before any timed assertion and the reader-idle window is kept comfortably below the tool's
 * runtime.
 *
 * @author Konstantin Pavlov
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgressKeepAliveTest {

    private static final Duration READER_IDLE = Duration.ofMillis(250);
    private static final long SLOW_SLEEP_MS = 2_000L;

    private static final String TOOL_CALL = // language=JSON
            """
            {"jsonrpc":"2.0","id":1,"method":"tools/call",
             "params":{"name":"slow-progress","arguments":{}}}
            """;

    private Server server;
    private NettyServer nettyServer;
    private int port;

    @BeforeAll
    void startServer() throws Exception {
        server = TachyonServer.builder()
                .session(s -> s.enabled(true))
                .tool(new ProgressHandler("warmup", 0))
                .tool(new ProgressHandler("slow-progress", SLOW_SLEEP_MS))
                .build();
        var config = new NettyServerConfig(
                "127.0.0.1",
                0,
                "/mcp",
                READER_IDLE,
                Duration.ofMinutes(5),
                McpChannelInitializer.DEFAULT_MAX_CONTENT_LENGTH,
                NettyServerConfig.buildCorsConfig(null, false, false, null),
                NettyIoEngine.AUTO,
                null);
        nettyServer = new NettyServer(server, config);
        port = nettyServer.port();
        warmUp();
    }

    @AfterAll
    void stopServer() {
        if (nettyServer != null) nettyServer.close();
        if (server != null) server.close();
    }

    /**
     * JIT-warms the POST → dispatch → {@code doStart} → {@code SseHeartbeat.enable} path so the
     * timed tests flush their first progress event sub-millisecond and never lose the race against
     * the reader-idle timer.
     */
    private void warmUp() throws Exception {
        try (var client = new TestMcpClient(port)) {
            var sessionId = client.initialize();
            client.sendRequest(
                    sessionId, // language=JSON
                    """
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"warmup","arguments":{}}}
                    """);
        }
    }

    @Test
    void progressNotificationUpgradesPostToSSE() throws Exception {
        callSlowProgressAndAssertSse();
    }

    @Test
    void progressKeepAliveSurvivesReaderIdle() throws Exception {
        var lines = new CopyOnWriteArrayList<String>();
        try (var client = new TestMcpClient(port)) {
            var sessionId = client.initialize();
            var response = client.sendStreamingRequest(sessionId, TOOL_CALL);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("text/event-stream");
            var consume = CompletableFuture.runAsync(() -> response.body().forEach(lines::add));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(lines)
                            .as("reader-idle must emit an SSE comment heartbeat, not close the channel")
                            .anyMatch(l -> l.startsWith(":")));
            consume.get(10, TimeUnit.SECONDS);
            var body = String.join("\n", lines);
            assertThat(body).contains("notifications/progress");
            assertThat(body).contains("done");
        }
    }

    /**
     * Calls {@code slow-progress}, asserts the shared SSE-upgrade contract (200, event-stream
     * content type, progress notification, tool result) and returns the accumulated SSE body.
     */
    private String callSlowProgressAndAssertSse() throws Exception {
        try (var client = new TestMcpClient(port)) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, TOOL_CALL);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("text/event-stream");
            var body = response.body();
            assertThat(body).contains("notifications/progress");
            assertThat(body).contains("done");
            return body;
        }
    }

    private static class ProgressHandler extends AbstractSyncToolHandler {

        private final long sleepMs;

        ProgressHandler(String name, long sleepMs) {
            super(ToolDescriptor.builder()
                    .name(name)
                    .description("Emits a progress notification then completes")
                    .build());
            this.sleepMs = sleepMs;
        }

        @Override
        public ToolResult handle(InteractionContext ctx, ToolArgs args) throws Exception {
            // A non-null progress token is required: DefaultMcpContext.progress discards a null
            // progressToken, so a keep-alive flush needs a token — mirroring the real keep-alive
            // which forwards the client's _meta.progressToken. Without a server->client message the
            // POST stays buffered JSON and reader-idle would reap it.
            ctx.notifications().progress("keep-alive", 1, 1, "tick");
            if (sleepMs > 0) Thread.sleep(sleepMs);
            return ToolResult.text("done");
        }
    }
}
