/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.tachyonmcp.runtime.SessionState;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.internal.ServerEngine;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * E2E test for #75: a client that holds an SSE stream open but sends nothing must not be reaped by
 * the session janitor. A controlled pair proves the heartbeat is the load-bearing signal — same
 * setup, heartbeats on vs off, opposite outcomes.
 *
 * <p>Timings sized for slow CI runners: TTL=2s, janitorInterval=500ms, heartbeatInterval=500ms.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionJanitorOutboundActivityTest {

    private static final Duration TTL = ofSeconds(2);
    private static final Duration JANITOR_INTERVAL = ofMillis(500);
    private static final Duration HEARTBEAT_INTERVAL = ofMillis(500);

    private TachyonServer serverHandle;
    private int port;

    @BeforeAll
    void beforeAll() {
        serverHandle = TachyonServer.builder()
                .session(s -> s.enabled(true).sessionTtl(TTL).janitorInterval(JANITOR_INTERVAL))
                .network(n -> n.host("localhost").port(0).heartbeatInterval(HEARTBEAT_INTERVAL))
                .start();
        port = serverHandle.port();
    }

    @AfterAll
    void afterAll() {
        serverHandle.close();
    }

    /** A silent GET SSE stream stays alive past the TTL because heartbeat writes touch the session. */
    @Test
    void silentListeningStreamStaysAlive() throws Exception {
        var sessionId = initializeAndActivate(port);
        try (var sseSocket = openRawSse(port, sessionId);
                var reader = drainInBackground(sseSocket, 6000)) {
            // Well past the 2s TTL: the session must survive on heartbeats alone.
            await().atMost(ofSeconds(6)).pollInterval(ofMillis(200)).untilAsserted(() -> {
                var session = ((ServerEngine) serverHandle).getSession(sessionId);
                assertThat(session).isPresent();
                assertThat(session.get().state()).isEqualTo(SessionState.ACTIVE);
            });

            try (var client = new Mcp20251125TestClient(port)) {
                var ping = client.post(sessionId, """
                    {"jsonrpc":"2.0","id":1,"method":"ping"}
                    """);
                assertThat(ping.statusCode()).isEqualTo(200);
                assertThat(ping.body()).contains("result");
            }
        }
    }

    /** Same setup with heartbeats disabled: the silent stream produces no writes, so it is reaped. */
    @Test
    void silentStreamReapedWhenHeartbeatsDisabled() throws Exception {
        try (var noHbServer = TachyonServer.builder()
                .session(s -> s.enabled(true).sessionTtl(TTL).janitorInterval(JANITOR_INTERVAL))
                .network(n -> n.host("localhost").port(0).heartbeatInterval(Duration.ZERO))
                .start()) {
            var noHbPort = noHbServer.port();
            var sessionId = initializeAndActivate(noHbPort);
            try (var sseSocket = openRawSse(noHbPort, sessionId);
                    var reader = drainInBackground(sseSocket, 6000)) {
                await().atMost(ofSeconds(6))
                        .pollInterval(ofMillis(200))
                        .untilAsserted(() -> assertThat(((ServerEngine) noHbServer).getSession(sessionId))
                                .isEmpty());
            }
        }
    }

    // ---- helpers ----

    /** Keeps reading the socket in a background thread until {@code durationMs} elapses. */
    private static AutoCloseable drainInBackground(Socket socket, long durationMs) {
        var deadline = System.currentTimeMillis() + durationMs;
        var future = CompletableFuture.runAsync(() -> {
            var buf = new byte[4096];
            while (System.currentTimeMillis() < deadline) {
                try {
                    socket.setSoTimeout(100);
                    if (socket.getInputStream().read(buf) < 0) break;
                } catch (SocketTimeoutException e) {
                    // expected — poll until deadline
                } catch (Exception e) {
                    break;
                }
            }
        });
        return () -> future.cancel(true);
    }

    private String initializeAndActivate(int targetPort) throws Exception {
        try (var client = new Mcp20251125TestClient(targetPort)) {
            return client.initialize();
        }
    }

    static Socket openRawSse(int targetPort, String sessionId) throws Exception {
        var socket = new Socket("localhost", targetPort);
        var req = ("GET /mcp HTTP/1.1\r\n"
                        + "Host: localhost:" + targetPort + "\r\n"
                        + "MCP-Session-Id: " + sessionId + "\r\n"
                        + "Accept: text/event-stream\r\n"
                        + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
        socket.getOutputStream().write(req);
        socket.getOutputStream().flush();
        var sb = new StringBuilder();
        var buf = new byte[1];
        socket.setSoTimeout(5000);
        while (!sb.toString().endsWith("\r\n\r\n")) {
            if (socket.getInputStream().read(buf) < 0) break;
            sb.append((char) buf[0]);
        }
        assertThat(sb.toString()).contains("200 OK");
        return socket;
    }
}
