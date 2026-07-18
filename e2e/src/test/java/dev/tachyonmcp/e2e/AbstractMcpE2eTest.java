/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.ServerBuilder;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.transport.netty.NettyServer;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractMcpE2eTest {

    protected TachyonServer server;
    protected NettyServer nettyServer;
    protected int port;
    protected boolean usingCustomServer;

    enum SessionMode {
        STATEFUL,
        STATELESS
    }

    /** Each subclass declares its session policy once. */
    protected abstract SessionMode sessionMode();

    /** Exposes the internal engine SPI for tests that need server introspection. */
    protected ServerEngine engine() {
        return (ServerEngine) server;
    }

    @BeforeAll
    void beforeAll() {
        startDefaultServer();
    }

    @AfterAll
    void tearDown() {
        closeCustomServerIfRunning();
    }

    protected TestMcpClient createTestClient() {
        return new TestMcpClient(port);
    }

    // region: ---- McpServer lifecycle management (call from subclass setup / teardown) ----

    /** Starts the session-mode-appropriate shared singleton server. */
    protected void startDefaultServer() {
        var h = SharedE2eServer.ensureStarted();
        this.server = h;
        this.port = h.port();
        this.usingCustomServer = false;
    }

    protected void startEmptyServer() {
        startServer(b -> {});
    }

    /**
     * Builds and starts a fresh server. The test consumer is applied FIRST, then the parent's
     * {@link #sessionMode()} is enforced — tests cannot accidentally override session policy.
     */
    protected final void startServer(Consumer<ServerBuilder> configurer) {
        closeCustomServerIfRunning();
        var builder = TachyonServer.builder();
        configurer.accept(builder);
        builder.session(s -> s.enabled(sessionMode() == SessionMode.STATEFUL));
        this.server = builder.build();
        this.nettyServer = new NettyServer(0, (ServerEngine) server);
        this.port = nettyServer.port();
        this.usingCustomServer = true;
    }

    private void closeCustomServerIfRunning() {
        if (usingCustomServer) {
            nettyServer.close();
            server.close();
            nettyServer = null;
            server = null;
            usingCustomServer = false;
        }
    }

    protected void stopServer() {
        if (usingCustomServer) {
            nettyServer.close();
            server.close();
            nettyServer = null;
            server = null;
            usingCustomServer = false;
        }
    }

    // endregion

    // region: ---- JSON-RPC helpers ----

    /** Parses {@code "id":<n>} or {@code "id":"<s>"} out of a JSON-RPC request body. */
    protected static String extractRequestId(String requestBody) {
        var idx = requestBody.indexOf("\"id\"");
        if (idx < 0) return "";
        var colon = requestBody.indexOf(':', idx);
        if (colon < 0) return "";
        var sb = new StringBuilder();
        for (int i = colon + 1; i < requestBody.length(); i++) {
            var c = requestBody.charAt(i);
            if (c == ',' || c == '}') break;
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Walks all {@code data:} lines in an SSE body and returns the JSON payload whose JSON-RPC
     * envelope has an id matching {@code requestId}. Falls back to the last data line if no match.
     */
    protected static String extractJsonRpcResponse(String sseBody, String requestId) {
        String last = null;
        var idMarker = "\"id\":" + requestId;
        for (var line : sseBody.split("\n")) {
            String data = null;
            if (line.startsWith("data: ")) data = line.substring("data: ".length());
            else if (line.startsWith("data:")) data = line.substring("data:".length());
            if (data == null) continue;
            last = data;
            if (!requestId.isEmpty() && data.contains(idMarker)) {
                return data;
            }
        }
        assertThat(last).as("SSE response must contain at least one data line").isNotNull();
        return last;
    }

    // endregion
}
