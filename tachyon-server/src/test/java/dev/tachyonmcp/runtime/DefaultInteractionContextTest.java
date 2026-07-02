/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.protocol.Protocol;
import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import io.netty.handler.codec.http.HttpRequest;
import org.junit.jupiter.api.Test;

class DefaultInteractionContextTest {

    @Test
    void notificationsThrowsUnsupportedOperationException() {
        var ctx = new DefaultInteractionContext(new FakeProtocol());
        assertThatThrownBy(ctx::notifications)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("notifications");
    }

    @Test
    void sendRequestReturnsFailedFuture() {
        var ctx = new DefaultInteractionContext(new FakeProtocol());
        assertThat(ctx.sendRequest("test", null))
                .isCompletedExceptionally()
                .failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableThat()
                .withCauseInstanceOf(UnsupportedOperationException.class)
                .withMessageContaining("sendRequest");
    }

    private static class FakeProtocol implements Protocol {

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
        public boolean matches(HttpRequest request) {
            return true;
        }

        @Override
        public ProtocolResponseMapper responseMapper() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MutableInteractionContext createInteractionContext() {
            throw new UnsupportedOperationException();
        }
    }
}
