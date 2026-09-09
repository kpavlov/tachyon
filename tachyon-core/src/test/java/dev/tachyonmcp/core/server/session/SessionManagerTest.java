/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.domain.LoggingLevel;
import dev.tachyonmcp.core.protocol.Protocols;
import dev.tachyonmcp.core.runtime.SessionState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    @Test
    void persistsRuntimeStateAsImmutableSnapshot() {
        var store = new InMemorySessionStore();
        var manager = manager(store);
        var session = manager.createSession("s1");
        var protocol = Protocols.list().getFirst();

        session.protocol(protocol);
        session.enableExtension("io.tachyon/test");
        session.loggingLevel(LoggingLevel.WARNING);
        session.activate();
        session.touch();

        assertThat(store.find("s1")).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.key().sessionId()).isEqualTo("s1");
            assertThat(snapshot.state()).isEqualTo(SessionState.ACTIVE);
            assertThat(snapshot.protocolVersion()).isEqualTo(protocol.versionString());
            assertThat(snapshot.enabledExtensionIds()).containsExactly("io.tachyon/test");
            assertThat(snapshot.loggingLevel()).isEqualTo(LoggingLevel.WARNING);
            assertThat(snapshot.expiresAt()).isEqualTo(NOW.plusSeconds(30));
            assertThat(snapshot.revision()).isPositive();
        });
    }

    @Test
    void hydratesLocalRuntimeFromSnapshot() {
        var store = new InMemorySessionStore();
        var protocol = Protocols.list().getFirst();
        var key = new SessionKey("s1", "generation");
        var created = store.create(key, NOW.plusSeconds(30));
        var persisted = new SessionSnapshot(
                key,
                SessionState.ACTIVE,
                protocol.versionString(),
                Set.of("io.tachyon/test"),
                LoggingLevel.ERROR,
                created.expiresAt(),
                created.revision() + 1);
        assertThat(store.compareAndSet(created, persisted)).isTrue();

        var session = manager(store).getSession("s1").orElseThrow();

        assertThat(session.state()).isEqualTo(SessionState.ACTIVE);
        assertThat(session.protocol()).isSameAs(protocol);
        assertThat(session.isExtensionEnabled("io.tachyon/test")).isTrue();
        assertThat(session.loggingLevel()).isEqualTo(LoggingLevel.ERROR);
    }

    @Test
    void expiredSnapshotDoesNotHydrate() {
        var store = new InMemorySessionStore();
        var key = new SessionKey("s1", "generation");
        store.create(key, NOW.minusSeconds(1));

        assertThat(manager(store).getSession("s1")).isEmpty();
        assertThat(store.find("s1")).isEmpty();
    }

    @Test
    void snapshotWithUnsupportedProtocolDoesNotHydrate() {
        final var store = new InMemorySessionStore();
        final var key = new SessionKey("s1", "generation");
        final var created = store.create(key, NOW.plusSeconds(30));
        final var incompatible = new SessionSnapshot(
                key,
                SessionState.ACTIVE,
                "unsupported-version",
                Set.of(),
                null,
                created.expiresAt(),
                created.revision() + 1);
        assertThat(store.compareAndSet(created, incompatible)).isTrue();

        assertThat(manager(store).getSession("s1")).isEmpty();
        assertThat(store.find("s1")).contains(incompatible);
    }

    @Test
    void touchRefreshesPersistedExpiryOnlyWhenHalfTheTtlHasElapsed() {
        final var store = new TrackingSessionStore();
        final var clock = new MutableClock(NOW);
        final var manager = new SessionManager(store, clock, Duration.ofSeconds(30));
        final var session = manager.createSession("s1");

        session.touch();
        clock.advance(Duration.ofSeconds(14));
        session.touch();

        assertThat(store.touchCount()).isZero();

        clock.advance(Duration.ofSeconds(1));
        session.touch();
        session.touch();

        assertThat(store.touchCount()).isOne();
        assertThat(store.find("s1"))
                .hasValueSatisfying(snapshot -> assertThat(snapshot.expiresAt()).isEqualTo(NOW.plusSeconds(45)));
    }

    @Test
    void touchEvictsOnlyLocalRuntimeAfterGenerationReplacement() {
        final var store = new TrackingSessionStore();
        final var clock = new MutableClock(NOW);
        final var manager = new SessionManager(store, clock, Duration.ofSeconds(30));
        final var session = manager.createSession("s1");
        final var replacementKey = new SessionKey("s1", "replacement");
        final var replacement = store.create(replacementKey, NOW.plusSeconds(60));
        clock.advance(Duration.ofSeconds(15));

        session.touch();

        assertThat(session.state()).isEqualTo(SessionState.CLOSED);
        assertThat(manager.allSessions()).isEmpty();
        assertThat(store.find("s1")).contains(replacement);
        assertThat(store.terminateCount()).isZero();
    }

    @Test
    void sameIdCreationsDoNotOverlapStoreAndKeepOneGeneration() throws Exception {
        final var store = new TrackingSessionStore();
        final var manager = manager(store);
        manager.createSession("s1");
        store.armCreateTracking();

        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var first = executor.submit(() -> manager.createSession("s1"));
            assertThat(store.awaitFirstCreate()).isTrue();
            final var second = executor.submit(() -> {
                store.secondCreateAttempted();
                return manager.createSession("s1");
            });

            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        }

        final var local = manager.getSession("s1").orElseThrow();
        final var persisted = store.find("s1").orElseThrow();
        assertThat(store.maxConcurrentCreates()).isOne();
        assertThat(local.key()).isEqualTo(persisted.key());
    }

    @Test
    void hashCollidingIdsDoNotSerializeStoreCreation() throws Exception {
        final var firstId = "Aa";
        final var secondId = "BB";
        assertThat(firstId.hashCode()).isEqualTo(secondId.hashCode());
        final var store = new TrackingSessionStore();
        final var manager = manager(store);
        store.armCreateTracking();

        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var first = executor.submit(() -> manager.createSession(firstId));
            assertThat(store.awaitFirstCreate()).isTrue();
            final var second = executor.submit(() -> {
                store.secondCreateAttempted();
                return manager.createSession(secondId);
            });

            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        }

        assertThat(store.maxConcurrentCreates()).isEqualTo(2);
        assertThat(manager.getSession(firstId).orElseThrow().key())
                .isEqualTo(store.find(firstId).orElseThrow().key());
        assertThat(manager.getSession(secondId).orElseThrow().key())
                .isEqualTo(store.find(secondId).orElseThrow().key());
    }

    @Test
    void sweepEvictsExpiredLocalSessionAndSnapshot() {
        var store = new InMemorySessionStore();
        var manager = manager(store);
        var session = manager.createSession("s1");

        manager.sweep(-1);

        assertThat(session.state()).isEqualTo(SessionState.CLOSED);
        assertThat(store.find("s1")).isEmpty();
    }

    @Test
    void sweepKeepsFreshSession() {
        var store = new InMemorySessionStore();
        var manager = manager(store);
        var session = manager.createSession("s1");
        session.activate();

        manager.sweep(Long.MAX_VALUE);

        assertThat(session.state()).isEqualTo(SessionState.ACTIVE);
        assertThat(manager.getSession("s1")).containsSame(session);
        assertThat(store.find("s1")).isPresent();
    }

    private static SessionManager manager(SessionStore store) {
        return new SessionManager(store, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));
    }

    private static final class TrackingSessionStore implements SessionStore {
        private final InMemorySessionStore delegate = new InMemorySessionStore();
        private final AtomicInteger touchCount = new AtomicInteger();
        private final AtomicInteger terminateCount = new AtomicInteger();
        private final AtomicInteger concurrentCreates = new AtomicInteger();
        private final AtomicInteger maxConcurrentCreates = new AtomicInteger();
        private final CountDownLatch firstCreateEntered = new CountDownLatch(1);
        private final CountDownLatch secondCreateAttempted = new CountDownLatch(1);
        private final CountDownLatch overlappingCreateEntered = new CountDownLatch(1);
        private volatile boolean trackCreates;

        @Override
        public SessionSnapshot create(SessionKey key, Instant expiresAt) {
            if (!trackCreates) {
                return delegate.create(key, expiresAt);
            }
            final var concurrent = concurrentCreates.incrementAndGet();
            maxConcurrentCreates.accumulateAndGet(concurrent, Math::max);
            try {
                if (concurrent == 1) {
                    firstCreateEntered.countDown();
                    await(secondCreateAttempted);
                    await(overlappingCreateEntered, 2, TimeUnit.SECONDS);
                } else {
                    overlappingCreateEntered.countDown();
                }
                return delegate.create(key, expiresAt);
            } finally {
                concurrentCreates.decrementAndGet();
            }
        }

        @Override
        public Optional<SessionSnapshot> find(String sessionId) {
            return delegate.find(sessionId);
        }

        @Override
        public boolean compareAndSet(SessionSnapshot expected, SessionSnapshot updated) {
            return delegate.compareAndSet(expected, updated);
        }

        @Override
        public boolean touch(SessionKey key, Instant expiresAt) {
            touchCount.incrementAndGet();
            return delegate.touch(key, expiresAt);
        }

        @Override
        public boolean terminate(SessionKey key) {
            terminateCount.incrementAndGet();
            return delegate.terminate(key);
        }

        @Override
        public void close() {
            delegate.close();
        }

        void armCreateTracking() {
            trackCreates = true;
        }

        boolean awaitFirstCreate() throws InterruptedException {
            return firstCreateEntered.await(2, TimeUnit.SECONDS);
        }

        void secondCreateAttempted() {
            secondCreateAttempted.countDown();
        }

        int touchCount() {
            return touchCount.get();
        }

        int terminateCount() {
            return terminateCount.get();
        }

        int maxConcurrentCreates() {
            return maxConcurrentCreates.get();
        }

        private static void await(CountDownLatch latch) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        private static void await(CountDownLatch latch, long timeout, TimeUnit unit) {
            try {
                latch.await(timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        private MutableClock(Instant now) {
            this.now = new AtomicReference<>(now);
        }

        void advance(Duration duration) {
            now.updateAndGet(current -> current.plus(duration));
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
            return now.get();
        }
    }
}
