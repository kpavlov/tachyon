/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.server.domain.FormInputRequest;
import dev.tachyonmcp.api.server.domain.InputRequestBundle;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class TaskEntryTest {

    private static TaskEntry entry(Duration ttl) {
        return TaskEntry.builder("task-1").status(TaskState.WORKING).ttl(ttl).build();
    }

    private static TaskEntry workingEntry() {
        return entry(null);
    }

    private static InputRequestBundle bundleOf(Map<String, String> requestedKeysToPrompts, String requestState) {
        Map<String, dev.tachyonmcp.api.server.domain.InputRequest> requests = new HashMap<>();
        requestedKeysToPrompts.forEach(
                (key, prompt) -> requests.put(key, FormInputRequest.of(prompt, JsonSchema.objectSchema())));
        return new InputRequestBundle(requests, requestState);
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
        var entry = TaskEntry.builder("clock-1")
                .status(TaskState.WORKING)
                .ttl(Duration.ofMinutes(10))
                .clock(clock)
                .build();
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

    @Test
    void aTaskWithoutTtlNeverExpires() {
        // Given a task with no ttl at all
        var entry = TaskEntry.builder("no-ttl").build();

        // Then it stays alive however long we wait
        assertThat(entry.isExpired()).isFalse();
    }

    @Test
    void aTaskExpiresOnceItsTtlElapses() {
        // Given a task with a 10 minute ttl
        var clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
        var entry = TaskEntry.builder("ttl-1")
                .status(TaskState.WORKING)
                .ttl(Duration.ofMinutes(10))
                .clock(clock)
                .build();

        // When less than the ttl passes
        clock.advance(Duration.ofMinutes(9));

        // Then it is still current
        assertThat(entry.isExpired()).isFalse();

        // And once the ttl is past, it is expired
        clock.advance(Duration.ofMinutes(2));
        assertThat(entry.isExpired()).isTrue();
    }

    @Test
    void aCompletedTaskKeepsItsResultForTheKeepAliveWindowOnly() {
        // Given a completed task whose result is retained for 5 minutes
        var clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
        var entry = TaskEntry.builder("keep-1")
                .status(TaskState.WORKING)
                .keepAlive(Duration.ofMinutes(5))
                .clock(clock)
                .build();
        entry.complete(new TaskResult.Completed(null));

        // When part of the window has passed
        clock.advance(Duration.ofMinutes(4));

        // Then the result is still retrievable
        assertThat(entry.isResultExpired()).isFalse();

        // And once the window closes, it is eligible for eviction
        clock.advance(Duration.ofMinutes(2));
        assertThat(entry.isResultExpired()).isTrue();
    }

    @Test
    void aZeroKeepAliveRetainsTheResultForever() {
        // Given a completed task with keepAlive explicitly zeroed
        var clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
        var entry = TaskEntry.builder("keep-forever")
                .status(TaskState.WORKING)
                .keepAlive(Duration.ZERO)
                .clock(clock)
                .build();
        entry.complete(new TaskResult.Completed(null));

        // When a very long time passes
        clock.advance(Duration.ofDays(3650));

        // Then the result is never evicted
        assertThat(entry.isResultExpired()).isFalse();
    }

    @Test
    void submitInputForStaleRoundKeysCannotResumeANewerRound() {
        var entry = workingEntry();
        entry.requireInput(bundleOf(Map.of("key1", "Q1"), "round-1"), null);
        entry.submitInput(Map.of("key1", "answer-1")); // resumes -> WORKING

        entry.requireInput(bundleOf(Map.of("key2", "Q2"), "round-2"), null); // now parked on round 2

        // A stale submission carrying round 1's key arrives after round 2 has already begun.
        var result = entry.submitInput(Map.of("key1", "late-answer"));

        assertThat(result).isNull();
        assertThat(entry.status()).isEqualTo(TaskState.INPUT_REQUIRED);
    }

    @Test
    void submitInputAcceptsNullResponseValue() {
        var entry = workingEntry();
        entry.requireInput(bundleOf(Map.of("optional", "Optional?"), null), null);

        var responses = new HashMap<String, Object>();
        responses.put("optional", null);
        var result = entry.submitInput(responses);

        assertThat(result).isNotNull();
        assertThat(result.inputResponses()).containsKey("optional");
        assertThat(result.inputResponses().get("optional")).isNull();
    }

    @Test
    void concurrentSubmitInputResumesExactlyOnce() throws InterruptedException {
        var entry = workingEntry();
        entry.requireInput(bundleOf(Map.of("key1", "Q1", "key2", "Q2"), null), null);
        entry.submitInput(Map.of("key1", "answer-1")); // one key satisfied, one outstanding

        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var results = new CopyOnWriteArrayList<TaskEntry.ResumeInputs>();
        Runnable submitLastKey = () -> {
            ready.countDown();
            try {
                go.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            var result = entry.submitInput(Map.of("key2", "answer-2"));
            if (result != null) results.add(result);
        };
        var t1 = new Thread(submitLastKey);
        var t2 = new Thread(submitLastKey);
        t1.start();
        t2.start();
        ready.await();
        go.countDown();
        t1.join();
        t2.join();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).inputResponses()).isEqualTo(Map.of("key1", "answer-1", "key2", "answer-2"));
        assertThat(entry.status()).isEqualTo(TaskState.WORKING);
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
