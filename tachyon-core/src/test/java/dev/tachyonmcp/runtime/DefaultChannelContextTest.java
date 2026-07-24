/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.Protocol;
import dev.tachyonmcp.runtime.InteractionContext.Lifecycle;
import org.junit.jupiter.api.Test;

class DefaultChannelContextTest {

    @Test
    void shouldCreateContextWithProtocol() {
        var ctx = new DefaultChannelContext(new FakeProtocol());
        InteractionContext handlerContext = ctx;

        assertThat(ctx.protocol()).isNotNull();
        assertThat(handlerContext.protocolVersion()).isEqualTo("1.0");
        assertThat(handlerContext.sessionId()).isNull();
        assertThat(ctx.lifecycle()).isEqualTo(Lifecycle.INITIALIZATION);
        assertThat(ctx.session()).isNull();
    }

    @Test
    void shouldTrackLifecycle() {
        var ctx = new DefaultChannelContext(new FakeProtocol());

        ctx.setLifecycle(Lifecycle.OPERATION);
        assertThat(ctx.lifecycle()).isEqualTo(Lifecycle.OPERATION);
    }

    @Test
    void shouldExposeSessionIdWithoutExposingSession() {
        var ctx = new DefaultChannelContext(new FakeProtocol());
        InteractionContext handlerContext = ctx;

        ctx.setSession(new Session("sess-1", SseConnection.NOOP));

        assertThat(handlerContext.sessionId()).isEqualTo("sess-1");
    }

    private static final class FakeProtocol implements Protocol {

        @Override
        public String endpoint() {
            return "/test";
        }

        @Override
        public String familyName() {
            return "test";
        }

        @Override
        public String versionString() {
            return "1.0";
        }

        @Override
        public boolean matches(io.netty.handler.codec.http.HttpRequest request) {
            return false;
        }

        @Override
        public dev.tachyonmcp.protocol.ProtocolResponseMapper responseMapper() {
            return null;
        }

        @Override
        public ChannelContext createInteractionContext() {
            return new DefaultChannelContext(this);
        }
    }
}
