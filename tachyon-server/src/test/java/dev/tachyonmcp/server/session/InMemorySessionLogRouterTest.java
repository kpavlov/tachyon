/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

class InMemorySessionLogRouterTest {

    private static SessionEvent requestEvent(String sessionId, int i) {
        return new SessionEvent.RequestEvent(sessionId, i, "ping", "{}", System.currentTimeMillis());
    }

    @Test
    void appendAndReplay() {
        List<SessionEvent> s2;
        try (var router = new InMemorySessionLogRouter()) {
            router.append(requestEvent("s1", 1));
            router.append(requestEvent("s1", 2));
            router.append(requestEvent("s2", 3));

            var s1 = router.replay("s1", -1);
            assertThat(s1).hasSize(2).allMatch(e -> e.sessionId().equals("s1"));

            s2 = router.replay("s2", -1);
        }
        assertThat(s2).hasSize(1).allMatch(e -> e.sessionId().equals("s2"));
    }

    @Test
    void replayFromOffset() {
        var router = new InMemorySessionLogRouter();
        for (int i = 0; i < 10; i++) {
            router.append(requestEvent("s1", i));
        }
        var result = router.replay("s1", 5);
        assertThat(result).hasSize(5);
    }

    @Test
    void closeClears() {
        var router = new InMemorySessionLogRouter();
        router.append(requestEvent("s1", 1));
        router.close();
        assertThat(router.replay("s1", -1)).isEmpty();
    }

    @RetryingTest(maxAttempts = 3)
    void throughput() throws Exception {
        int threads = 8;
        int eventsPerThread = 10_000;

        long lockFreeOpsPerSec = measure(threads, eventsPerThread);

        System.out.printf("[InMemorySessionLogRouter]: %,d ops/sec%n", lockFreeOpsPerSec, (double) lockFreeOpsPerSec);

        assertThat(lockFreeOpsPerSec).as("Performance baseline").isGreaterThan(800_000);
    }

    private long measure(int threads, int eventsPerThread) throws Exception {
        // AFTER: ConcurrentLinkedQueue — O(1) CAS append
        var router = new InMemorySessionLogRouter();
        var latch = new CountDownLatch(1);
        var total = new AtomicLong(0);
        int ops = threads * eventsPerThread;

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                exec.submit(() -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < eventsPerThread; i++) {
                        router.append(requestEvent("sess_" + tid, i));
                        total.incrementAndGet();
                    }
                });
            }
            var start = System.nanoTime();
            latch.countDown();
            exec.shutdown();
            exec.awaitTermination(60, TimeUnit.SECONDS);
            var elapsedMs = Math.max(1, (System.nanoTime() - start) / 1_000_000);
            return (long) ops * 1000 / elapsedMs;
        }
    }

    @Test
    void concurrentAppendCorrectness() throws Exception {
        var router = new InMemorySessionLogRouter();
        int threads = 8;
        int eventsPerThread = 10_000;
        var latch = new CountDownLatch(1);
        var totalAppended = new AtomicLong(0);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                exec.submit(() -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < eventsPerThread; i++) {
                        router.append(requestEvent("sess_" + tid, i));
                        totalAppended.incrementAndGet();
                    }
                });
            }
            latch.countDown();
            exec.shutdown();
            exec.awaitTermination(30, TimeUnit.SECONDS);
        }

        assertThat(totalAppended.get()).isEqualTo((long) threads * eventsPerThread);

        // The log is bounded: 80k appends against a 10k window retain exactly the newest window.
        // Concurrent correctness for a bounded log means: nothing torn or duplicated, and each
        // session's surviving events are an ordered suffix of what that session appended.
        // A session may retain NOTHING (it finished early and later appends evicted its whole
        // tail) — but if anything survives, eviction is oldest-first, so the survivors must be
        // the contiguous tail ending at that session's final event.
        var totalRetained = 0;
        for (int t = 0; t < threads; t++) {
            var result = router.replay("sess_" + t, -1);
            totalRetained += result.size();
            if (result.isEmpty()) {
                continue;
            }
            var ids = result.stream()
                    .map(e -> (int) (Integer) ((SessionEvent.RequestEvent) e).requestId())
                    .toList();
            assertThat(ids.getLast()).isEqualTo(eventsPerThread - 1);
            for (int i = 1; i < ids.size(); i++) {
                assertThat(ids.get(i)).isEqualTo(ids.get(i - 1) + 1);
            }
        }
        assertThat(totalRetained).isEqualTo(router.maxEvents);
    }

    @Test
    void trimDropsOldestAndKeepsCursorSemantics() {
        var router = new InMemorySessionLogRouter();
        int overflow = 100;
        int total = router.maxEvents + overflow;
        for (int i = 0; i < total; i++) {
            router.append(requestEvent("s1", i));
        }

        var all = router.replay("s1", -1);
        assertThat(all).hasSize(router.maxEvents);
        var first = (SessionEvent.RequestEvent) all.getFirst();
        assertThat((Integer) first.requestId()).isEqualTo(overflow);

        // Global-index cursor from before the window is clamped, not misaligned:
        // replaying from an index inside the window resumes at the right event.
        var tail = router.replay("s1", total - 5);
        assertThat(tail).hasSize(5);
        var tailFirst = (SessionEvent.RequestEvent) tail.getFirst();
        assertThat((Integer) tailFirst.requestId()).isEqualTo(total - 5);
    }
}
