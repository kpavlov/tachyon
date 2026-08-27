/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import static dev.tachyonmcp.core.test.TestUtils.newEngine;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.features.tasks.TaskExecutionEngine;
import dev.tachyonmcp.api.server.features.tasks.TaskFeature;
import dev.tachyonmcp.api.server.features.tasks.TaskInput;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.server.config.TasksConfig;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultTaskRegistryTest {

    private final ServerEngine server = newEngine(builder -> {});
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-27T07:00:00Z"));
    private final DefaultTaskRegistry registry = new DefaultTaskRegistry(
            server,
            TasksConfig.builder()
                    .enabled(true)
                    .taskExecutionEngine(new StubTaskExecutionEngine())
                    .keepAlive(Duration.ofMinutes(5))
                    .pollInterval(Duration.ofSeconds(2))
                    .build(),
            clock);

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void publishAcceptsOnlyNewerRevisions() {
        var revisionOne = snapshot("task-1", TaskState.WORKING, 1);
        var revisionTwo = snapshot("task-1", TaskState.COMPLETED, 2);

        assertThat(registry.publish(revisionOne).revision()).isEqualTo(1);
        var storedRevisionTwo = registry.publish(revisionTwo);
        assertThat(storedRevisionTwo.revision()).isEqualTo(2);
        assertThat(registry.publish(revisionOne)).isEqualTo(storedRevisionTwo);
        assertThat(registry.get("task-1")).isEqualTo(storedRevisionTwo);
    }

    @Test
    void publishAppliesConfiguredPollIntervalWithoutChangingCallerSnapshot() {
        var snapshot = snapshot("task-1", TaskState.WORKING, 1);

        var published = registry.publish(snapshot);

        assertThat(snapshot.pollInterval()).isNull();
        assertThat(published.pollInterval()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void janitorEvictsTerminalProjectionButNeverTransitionsActiveWork() {
        registry.publish(snapshot("active", TaskState.WORKING, 1));
        registry.publish(snapshot("terminal", TaskState.COMPLETED, 1));

        clock.advance(Duration.ofMinutes(6));
        registry.runJanitorSweep();

        assertThat(registry.get("active")).isNotNull();
        assertThat(registry.get("active").status()).isEqualTo(TaskState.WORKING);
        assertThat(registry.get("terminal")).isNull();
    }

    @Test
    void nonPositiveKeepAliveRetainsTerminalResults() {
        var terminal = snapshot("terminal", TaskState.COMPLETED, 1);
        var zeroRetention = new TaskEntry(terminal, null, null, Duration.ZERO, clock);
        var negativeRetention = new TaskEntry(terminal, null, null, Duration.ofSeconds(-1), clock);

        clock.advance(Duration.ofDays(1));

        assertThat(zeroRetention.isResultExpired()).isFalse();
        assertThat(negativeRetention.isResultExpired()).isFalse();
    }

    @Test
    void removeOnlyDropsProjection() {
        registry.publish(snapshot("task-1", TaskState.WORKING, 1));

        assertThat(registry.remove("task-1")).isTrue();
        assertThat(registry.remove("task-1")).isFalse();
        assertThat(registry.get("task-1")).isNull();
    }

    private TaskSnapshot snapshot(String taskId, TaskState status, long revision) {
        return TaskSnapshot.builder()
                .taskId(taskId)
                .status(status)
                .createdAt(clock.instant())
                .lastUpdatedAt(clock.instant())
                .revision(revision)
                .build();
    }

    private static final class StubTaskExecutionEngine implements TaskExecutionEngine {
        @Override
        public Set<TaskFeature> supportedFeatures() {
            return Set.of();
        }

        @Override
        public TaskSnapshot refresh(InteractionContext context, String taskId) {
            return null;
        }

        @Override
        public void cancel(InteractionContext context, String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void submitInput(InteractionContext context, String taskId, TaskInput input) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
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
