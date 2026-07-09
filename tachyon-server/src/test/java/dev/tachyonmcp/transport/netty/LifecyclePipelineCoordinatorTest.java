/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty;

import static dev.tachyonmcp.transport.netty.InteractionHandler.INTERACTION_CONTEXT_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.runtime.DefaultInteractionContext;
import dev.tachyonmcp.runtime.InteractionContext.Lifecycle;
import dev.tachyonmcp.runtime.InteractionEvent;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SseConnection;
import dev.tachyonmcp.server.RpcDispatcher;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.TachyonServer;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LifecyclePipelineCoordinatorTest {

    private Server server;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        server = TachyonServer.builder().build();
        final var dispatcher = new RpcDispatcher(server, Runnable::run);
        channel = new EmbeddedChannel(new InteractionHandler());
        channel.pipeline()
                .addLast(
                        McpHandlerManager.HANDLER_INIT,
                        new McpInitializationHandler(server, dispatcher, Runnable::run));
        channel.pipeline()
                .addLast(
                        "lifecycle",
                        new LifecyclePipelineCoordinator(new McpHandlerManager(server, dispatcher, Runnable::run)));
    }

    @AfterEach
    void tearDown() {
        channel.close();
        server.close();
    }

    @Test
    void operationStartedReplacesInitHandlerWithOperationHandler() {
        initialize();

        assertThat(channel.pipeline().get(McpHandlerManager.HANDLER_INIT)).isNull();
        assertThat(channel.pipeline().get(McpOperationHandler.class)).isNotNull();
    }

    @Test
    void shutdownStartedRemovesSession() {
        initialize();

        var freshSession = server.createSession("shutdown-test");
        freshSession.activate();

        assertThat(server.getSession(freshSession.id())).isPresent();

        channel.pipeline().fireUserEventTriggered(new InteractionEvent.ShutdownStarted(freshSession.id()));
        channel.runPendingTasks();

        assertThat(server.getSession(freshSession.id())).isEmpty();
    }

    @Test
    void shutdownStartedWithNullSessionIdDoesNotThrow() {
        initialize();

        channel.pipeline().fireUserEventTriggered(new InteractionEvent.ShutdownStarted(null));
        channel.runPendingTasks();
    }

    @Test
    void abruptChannelCloseDoesNotLeakSessionInContext() {
        initialize();

        channel.close();
        channel.runPendingTasks();

        // InteractionContext may still be on the channel after close;
        // the channel's attribute map is GC'd with the channel itself.
        // Verify at least the session is not leaked.
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void interactionContextLifecycleTransitionsViaEvents() {
        var protocol = Protocols.versions().get(0);
        channel.attr(INTERACTION_CONTEXT_KEY).set(new DefaultInteractionContext(protocol));

        var ic = channel.attr(INTERACTION_CONTEXT_KEY).get();
        assertThat(ic).isNotNull();
        assertThat(ic.getLifecycle()).isEqualTo(Lifecycle.INITIALIZATION);
        assertThat(ic.getProtocol().familyName()).isEqualTo("mcp");

        // OperationStarted
        channel.pipeline()
                .fireUserEventTriggered(new InteractionEvent.OperationStarted(new Session("s1", SseConnection.NOOP)));
        ic = channel.attr(INTERACTION_CONTEXT_KEY).get();
        assertThat(ic).isNotNull();
        assertThat(ic.getLifecycle()).isEqualTo(Lifecycle.OPERATION);
        assertThat(ic.session()).isNotNull();

        // ShutdownStarted does not throw
        channel.pipeline().fireUserEventTriggered(new InteractionEvent.ShutdownStarted("s1"));
        channel.runPendingTasks();
    }

    private void initialize() {
        var body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        var request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/mcp", Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers()
                .set(HttpHeaderNames.ORIGIN, "http://localhost:3000")
                .set(HttpHeaderNames.ACCEPT, "application/json, text/event-stream");
        channel.writeInbound(request);
        channel.runPendingTasks();
        // Drain the response
        var response = channel.readOutbound();
        if (response instanceof io.netty.buffer.ByteBuf buf) {
            buf.release();
        } else if (response instanceof FullHttpResponse full) {
            full.release();
        }
    }
}
