/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.Protocol;
import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.runtime.DefaultInteractionContext;
import dev.tachyonmcp.runtime.MutableInteractionContext;
import dev.tachyonmcp.server.session.DefaultMcpContext;
import dev.tachyonmcp.server.session.DispatchContext;
import io.netty.handler.codec.http.HttpRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class McpDispatcherProtocolContextTest {

    /**
     * Fake protocol that records context-factory invocations.
     */
    private static final class RecordingProtocol implements Protocol {

        final AtomicInteger contextsCreated = new AtomicInteger();
        final AtomicReference<@Nullable MutableInteractionContext> lastCreated = new AtomicReference<>();

        RecordingProtocol() {}

        @Override
        public String endpoint() {
            return "/mcp";
        }

        @Override
        public String familyName() {
            return "fake";
        }

        @Override
        public String versionString() {
            return "2099-01-01";
        }

        @Override
        public boolean matches(HttpRequest request) {
            return true;
        }

        @Override
        public ProtocolResponseMapper responseMapper() {
            return Protocols.versions().getFirst().responseMapper();
        }

        @Override
        public MutableInteractionContext createInteractionContext() {
            contextsCreated.incrementAndGet();
            var ic = new DefaultInteractionContext(this);
            lastCreated.set(ic);
            return ic;
        }
    }

    @Test
    void dispatchWrapsChannelContext() throws Exception {
        try (var server = TachyonServer.builder().build()) {
            var protocol = new RecordingProtocol();
            var session = server.createSession("sess_p1");
            session.activate();

            var handlerContext = new AtomicReference<@Nullable DispatchContext>();
            server.registerHandler(new RpcMethodHandler() {
                @Override
                public String method() {
                    return "test/capture";
                }

                @Override
                public Object handle(DispatchContext context, Object params) {
                    handlerContext.set(context);
                    return Map.of("ok", true);
                }
            });

            var dispatcher = new McpDispatcher(server, Runnable::run);
            var channelContext = protocol.createInteractionContext();
            var result = dispatcher
                    .dispatchRequestAsync(1, "test/capture", Map.of(), "sess_p1", null, channelContext)
                    .get(5, TimeUnit.SECONDS);

            assertThat(result).isInstanceOf(McpDispatcher.DispatchResult.Response.class);
            assertThat(protocol.contextsCreated).hasValue(1);
            assertThat(handlerContext.get())
                    .as("handler must receive a DispatchContext wrapping the channel context")
                    .isNotNull()
                    .isInstanceOf(DefaultMcpContext.class);
            assertThat(protocol.lastCreated).hasValue(channelContext);
            assertThat(handlerContext.get()).isNotNull();
            assertThat(handlerContext.get().getProtocol()).isSameAs(protocol);
            assertThat(handlerContext.get().session()).isSameAs(session);
        }
    }

    @Test
    void dispatchWorksWithBareChannelContext() throws Exception {
        try (var server = TachyonServer.builder().build()) {
            var protocol = new RecordingProtocol();
            server.createSession("sess_p2").activate();

            var dispatcher = new McpDispatcher(server, Runnable::run);
            var channelContext = protocol.createInteractionContext();

            var result = dispatcher
                    .dispatchRequestAsync(1, "ping", Map.of(), "sess_p2", null, channelContext)
                    .get(5, TimeUnit.SECONDS);

            assertThat(result).isInstanceOf(McpDispatcher.DispatchResult.Response.class);
        }
    }

    @Test
    void statelessDispatchHasNoSession() throws Exception {
        try (var server =
                TachyonServer.builder().session(s -> s.stateless(true)).build()) {
            var handlerContext = new AtomicReference<DispatchContext>();
            server.registerHandler("test/capture", new RpcMethodHandler() {
                @Override
                public String method() {
                    return "test/capture";
                }

                @Override
                public Object handle(DispatchContext context, Object params) {
                    handlerContext.set(context);
                    context.notifications().send("notifications/message", Map.of("level", "info"));
                    return Map.of("ok", true);
                }
            });

            var dispatcher = new McpDispatcher(server, Runnable::run);
            var result = dispatcher
                    .dispatchRequestAsync(1, "test/capture", Map.of(), null)
                    .get(5, TimeUnit.SECONDS);

            assertThat(result).isInstanceOf(McpDispatcher.DispatchResult.Response.class);
            var context = handlerContext.get();
            assertThat(context).isNotNull();
            assertThat(context.session())
                    .as("stateless dispatch must not fabricate a session")
                    .isNull();
            assertThat(context.getLoggingLevel()).isNull();
            assertThat(context.getProtocol().familyName()).isEqualTo("mcp");
            assertThat(context.sendRequest("sampling/createMessage", Map.of()))
                    .as("server-to-client requests without a session must fail fast")
                    .isCompletedExceptionally();
        }
    }
}
