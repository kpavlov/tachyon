/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.internal.NotificationLogSupport;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NotificationsTest {

    @Test
    void convenienceMethodsDelegateToLog() {
        var seen = new AtomicReference<Logged>();
        Notifications notifications = (level, logger, data) -> seen.set(new Logged(level, logger, data));

        notifications.warning("logger.x", "boom");

        assertThat(seen.get()).isEqualTo(new Logged(LoggingLevel.WARNING, "logger.x", "boom"));
    }

    @Test
    void logParamsBuildsWireShapeOmittingAbsentLogger() {
        var params = NotificationLogSupport.logParams(LoggingLevel.NOTICE, null, null);

        assertThat(params).containsEntry("level", "notice").containsEntry("data", null);
        assertThat(params).doesNotContainKey("logger");
        assertThat(NotificationLogSupport.LOG_METHOD).isEqualTo("notifications/message");
    }

    @Test
    void logParamsIncludesLoggerWhenPresent() {
        var params = NotificationLogSupport.logParams(LoggingLevel.WARNING, "logger.x", "boom");

        assertThat(params).containsEntry("level", "warning").containsEntry("logger", "logger.x");
    }

    private record Logged(LoggingLevel level, String logger, Object data) {}
}
