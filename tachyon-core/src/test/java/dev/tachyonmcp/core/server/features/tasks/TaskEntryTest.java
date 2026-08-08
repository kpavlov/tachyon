/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.features.tasks.TaskDescriptor;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.server.config.TasksConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TaskEntryTest {

    private static TaskEntry entry(Duration ttl) {
        return new TaskEntry(
                TaskDescriptor.builder().id("task-1").build(), "task-1", TaskState.WORKING, ttl, null, null);
    }

    @Test
    void zeroAndNegativeTtlMeanNeverExpires() {
        assertThat(entry(Duration.ZERO).ttl()).isNull();
        assertThat(entry(Duration.ZERO).ttlMillis()).isNull();
        assertThat(entry(Duration.ZERO).isExpired()).isFalse();

        assertThat(entry(Duration.ofSeconds(-1)).ttl()).isNull();
        assertThat(entry(Duration.ofSeconds(-1)).isExpired()).isFalse();
    }

    @Test
    void ttlMillisClampsInsteadOfOverflowing() {
        var entry = entry(Duration.ofSeconds(Long.MAX_VALUE));

        assertThat(entry.ttlMillis()).isEqualTo(Long.MAX_VALUE);
        assertThat(entry.isExpired()).isFalse();
    }

    @Test
    void positiveTtlIsPreserved() {
        var entry = entry(Duration.ofMinutes(5));

        assertThat(entry.ttl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(entry.ttlMillis()).isEqualTo(Duration.ofMinutes(5).toMillis());
    }

    @Test
    void controllableClockFlipsExpiryAndMovesLastUpdatedAt() {
        var clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
        var entry = new TaskEntry(
                TaskDescriptor.builder().id("clock-1").build(),
                "clock-1",
                TaskState.WORKING,
                Duration.ofMinutes(10),
                null,
                null,
                null,
                TasksConfig.DEFAULT_TASK_KEEP_ALIVE,
                null,
                ignored -> {},
                clock);
        var lastUpdated = entry.lastUpdatedAt();
        assertThat(entry.createdAt()).isEqualTo(clock.instant());
        assertThat(entry.isExpired()).isFalse();

        clock.advance(Duration.ofMinutes(5));
        assertThat(entry.isExpired()).isFalse();
        assertThat(entry.updateMessage("still working")).isTrue();
        assertThat(entry.lastUpdatedAt()).isAfter(lastUpdated);
        assertThat(entry.lastUpdatedAt()).isEqualTo(clock.instant());

        clock.advance(Duration.ofMinutes(11));
        assertThat(entry.isExpired()).isTrue();
    }

    /** A tiny mutable {@link Clock} so TTL/expiry can be driven deterministically. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
