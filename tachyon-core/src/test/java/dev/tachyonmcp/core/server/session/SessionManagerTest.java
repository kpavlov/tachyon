/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.core.runtime.Session;
import dev.tachyonmcp.core.runtime.SessionState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

    @Test
    void onSessionClosedFiresOnExplicitRemoval() {
        List<String> closed = new ArrayList<>();
        var manager = new SessionManager(closed::add);
        manager.createSession("s1");

        manager.removeSession("s1");

        assertThat(closed).containsExactly("s1");
    }

    @Test
    void onSessionClosedFiresOnSweepEviction() {
        List<String> closed = new ArrayList<>();
        var manager = new SessionManager(closed::add);
        manager.createSession("s1");

        manager.sweep(-1); // any idle time exceeds a negative TTL

        assertThat(closed).containsExactly("s1");
    }

    @Test
    void onSessionClosedDoesNotFireForUnknownSession() {
        List<String> closed = new ArrayList<>();
        var manager = new SessionManager(closed::add);

        manager.removeSession("never-created");

        assertThat(closed).isEmpty();
    }

    @Test
    void sweepEvictsExpiredSessions() {
        var manager = new SessionManager();
        var session = manager.createSession("s1");

        manager.sweep(-1); // any idle time exceeds a negative TTL

        assertThat(session.state()).isEqualTo(SessionState.CLOSED);
        assertThat(manager.getSession("s1")).isEmpty();
    }

    @Test
    void sweepKeepsFreshSessions() {
        var manager = new SessionManager();
        var session = manager.createSession("s1");
        session.activate();

        manager.sweep(Long.MAX_VALUE);

        assertThat(session.state()).isEqualTo(SessionState.ACTIVE);
        assertThat(manager.getSession("s1")).contains(session);
    }

    /**
     * The race {@link SessionManager#removeIfCurrent} exists for: the sweep iterates a snapshot
     * containing a stale session while the live table already holds a replacement created under
     * the same id (custom SessionIdGenerator scenario). The stale session must be closed, but
     * the replacement must survive the eviction.
     */
    @Test
    void removeIfCurrentEvictsOnlyTheExpectedInstance() {
        var manager = new SessionManager();
        var stale = manager.createSession("s1");

        // A replacement session appears under the same id (custom SessionIdGenerator scenario)
        // between the janitor's expiry check and its removal.
        var replacement = manager.createSession("s1");

        assertThat(manager.removeIfCurrent("s1", stale)).isFalse();
        assertThat(manager.getSession("s1")).contains(replacement);

        assertThat(manager.removeIfCurrent("s1", replacement)).isTrue();
        assertThat(manager.getSession("s1")).isEmpty();
    }

    @Test
    void getOrResumeSessionReturnsExistingLiveSessionWithoutConsultingHistory() {
        var manager = new SessionManager();
        var session = manager.createSession("s1");

        var resumed = manager.getOrResumeSession("s1", () -> {
            throw new AssertionError("hasHistory must not be consulted when a live session exists");
        });

        assertThat(resumed).contains(session);
    }

    @Test
    void getOrResumeSessionReturnsEmptyWhenNoLiveSessionAndNoHistory() {
        var manager = new SessionManager();

        var resumed = manager.getOrResumeSession("unknown", () -> false);

        assertThat(resumed).isEmpty();
    }

    @Test
    void getOrResumeSessionRecreatesFromHistoryWhenNoLiveSession() {
        var manager = new SessionManager();

        var resumed = manager.getOrResumeSession("s1", () -> true);

        assertThat(resumed).isPresent();
        assertThat(resumed.get().id()).isEqualTo("s1");
        assertThat(manager.getSession("s1")).contains(resumed.get());
    }

    /**
     * The bug the review finding caught: an explicitly-DELETE'd session's events remain in the
     * durable log (removeSession never touches SessionEventStore), so hasHistory alone would
     * happily resurrect it. explicitlyClosed must block that regardless of what hasHistory says.
     */
    @Test
    void getOrResumeSessionNeverResurrectsAnExplicitlyClosedSession() {
        var manager = new SessionManager();
        manager.createSession("s1");
        manager.removeSession("s1");

        var resumed = manager.getOrResumeSession("s1", () -> true);

        assertThat(resumed).isEmpty();
    }

    @Test
    void getOrResumeSessionStillResumesAfterTtlExpiryUnlikeExplicitClose() {
        var manager = new SessionManager();
        manager.createSession("s1");
        manager.sweep(-1); // TTL-expired, not explicitly closed

        var resumed = manager.getOrResumeSession("s1", () -> true);

        assertThat(resumed).isPresent();
    }

    /**
     * The race the review finding caught: two concurrent reconnects for the same unknown-to-the-
     * live-table id both used to independently call createSession, and the second silently
     * replaced and closed the first's session out from under it. getOrResumeSession must be
     * atomic per id — every racing caller gets back the exact same instance.
     */
    @Test
    void getOrResumeSessionIsAtomicUnderConcurrentReconnects() throws InterruptedException {
        var manager = new SessionManager();
        int threads = 32;
        var latch = new CountDownLatch(1);
        List<Optional<Session>> results = new CopyOnWriteArrayList<>();

        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    results.add(manager.getOrResumeSession("s1", () -> true));
                });
            }
            latch.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(results).hasSize(threads).allMatch(Optional::isPresent);
        var distinctInstances =
                Set.copyOf(results.stream().map(Optional::orElseThrow).toList());
        assertThat(distinctInstances)
                .as("every racing caller must observe the same Session instance")
                .hasSize(1);
    }
}
