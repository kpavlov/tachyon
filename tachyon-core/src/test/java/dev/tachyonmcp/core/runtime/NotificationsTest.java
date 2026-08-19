/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.runtime.Notifications;
import dev.tachyonmcp.api.server.domain.LoggingLevel;
import dev.tachyonmcp.core.server.internal.NotificationLogSupport;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class NotificationsTest {

    @Test
    void convenienceMethodsDelegateToLog() {
        var seen = new AtomicReference<@Nullable Logged>();
        Notifications notifications = (level, logger, data) -> seen.set(new Logged(level, logger, data));

        notifications.warning("logger.x", "boom");

        assertThat(seen.get()).isEqualTo(new Logged(LoggingLevel.WARNING, "logger.x", "boom"));
    }

    @Test
    void logMethodConstantMatchesTheSpec() {
        assertThat(NotificationLogSupport.LOG_METHOD).isEqualTo("notifications/message");
    }

    private record Logged(LoggingLevel level, String logger, Object data) {}
}
