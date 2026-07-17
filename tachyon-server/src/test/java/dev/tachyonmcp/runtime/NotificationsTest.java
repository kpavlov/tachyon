/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.LoggingLevel;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class NotificationsTest {

    @Test
    void logFunnelsThroughSendWithWireShape() {
        var sent = new AtomicReference<Sent>();
        var notifications = collecting(sent);

        notifications.log(LoggingLevel.NOTICE, null, null);

        assertThat(sent.get().method()).isEqualTo("notifications/message");
        var params = params(sent.get().params());
        assertThat(params).containsEntry("level", "notice").containsEntry("data", null);
        assertThat(params).doesNotContainKey("logger");
    }

    @Test
    void convenienceMethodsDelegateToLog() {
        var sent = new AtomicReference<Sent>();
        var notifications = collecting(sent);

        notifications.warning("logger.x", "boom");

        var params = params(sent.get().params());
        assertThat(params).containsEntry("level", "warning").containsEntry("logger", "logger.x");
    }

    @Test
    void shouldEmitFalseSuppressesSend() {
        var sent = new AtomicReference<Sent>();
        AbstractNotifications notifications = new AbstractNotifications() {
            @Override
            public boolean shouldEmit(LoggingLevel level) {
                return false;
            }

            @Override
            public void send(String method, Object params) {
                sent.set(new Sent(method, params));
            }

            @Override
            public void progress(@Nullable Object token, double progress, double total, String message) {}

            @Override
            public void comment(@Nullable String message) {}
        };

        notifications.error("logger.x", "boom");

        assertThat(sent.get()).isNull();
    }

    private static AbstractNotifications collecting(AtomicReference<Sent> sent) {
        return new AbstractNotifications() {
            @Override
            public void send(String method, Object params) {
                sent.set(new Sent(method, params));
            }

            @Override
            public void progress(@Nullable Object token, double progress, double total, String message) {}

            @Override
            public void comment(@Nullable String message) {}
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> params(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private record Sent(String method, Object params) {}
}
