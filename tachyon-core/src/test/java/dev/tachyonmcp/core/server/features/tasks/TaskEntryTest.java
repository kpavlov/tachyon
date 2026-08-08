/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.features.tasks.TaskDescriptor;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import java.time.Duration;
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
}
