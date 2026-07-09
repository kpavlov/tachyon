/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.tachyonmcp.runtime.SessionState;
import dev.tachyonmcp.server.ServerHandle;
import dev.tachyonmcp.server.TachyonServer;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
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

    private ServerHandle serverHandle;
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
        var sseSocket = openRawSse(port, sessionId);
        try (var reader = drainInBackground(sseSocket, 6000)) {
            // Well past the 2s TTL: the session must survive on heartbeats alone.
            await().atMost(ofSeconds(6)).pollInterval(ofMillis(200)).untilAsserted(() -> {
                var session = serverHandle.server().getSession(sessionId);
                assertThat(session).isPresent();
                assertThat(session.get().state()).isEqualTo(SessionState.ACTIVE);
            });

            var ping = sendJsonRpc(port, sessionId, """
                    {"jsonrpc":"2.0","id":1,"method":"ping"}
                    """);
            assertThat(ping.statusCode()).isEqualTo(200);
            assertThat(ping.body()).contains("result");
        } finally {
            sseSocket.close();
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
            var sseSocket = openRawSse(noHbPort, sessionId);
            try (var reader = drainInBackground(sseSocket, 6000)) {
                await().atMost(ofSeconds(6))
                        .pollInterval(ofMillis(200))
                        .untilAsserted(() -> assertThat(noHbServer.server().getSession(sessionId))
                                .isEmpty());
            } finally {
                sseSocket.close();
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
        try (var client = HttpClient.newHttpClient()) {
            var init = sendJsonRpc(client, targetPort, null, """
                    {"jsonrpc":"2.0","id":0,"method":"initialize",
                     "params":{"protocolVersion":"2025-11-25","capabilities":{},
                               "clientInfo":{"name":"test","version":"1.0"}}}
                    """);
            var sessionId = init.headers().firstValue("MCP-Session-Id").orElseThrow();
            sendJsonRpc(client, targetPort, sessionId, """
                    {"jsonrpc":"2.0","method":"notifications/initialized"}
                    """);
            return sessionId;
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

    private HttpResponse<String> sendJsonRpc(int targetPort, String sessionId, String body) throws Exception {
        return sendJsonRpc(HttpClient.newHttpClient(), targetPort, sessionId, body);
    }

    private HttpResponse<String> sendJsonRpc(HttpClient client, int targetPort, @Nullable String sessionId, String body)
            throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + targetPort + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-11-25")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            builder.header("MCP-Session-Id", sessionId);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
