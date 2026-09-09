/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.session;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.core.runtime.Session;
import dev.tachyonmcp.core.runtime.SessionState;
import dev.tachyonmcp.core.runtime.SseConnection;
import dev.tachyonmcp.core.server.internal.AbstractJanitor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates process-local session runtimes with immutable persisted snapshots. */
@InternalApi
public final class SessionManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CreationLock> creationLocks = new ConcurrentHashMap<>();
    private final SessionStore store;
    private final Clock clock;
    private volatile Duration ttl;
    private volatile Duration expiryRefreshMargin;
    private @Nullable AbstractJanitor janitor;

    /** Creates a manager using the system clock and a five-minute snapshot TTL. */
    public SessionManager(SessionStore store) {
        this(store, Clock.systemUTC(), Duration.ofMinutes(5));
    }

    /** Creates a manager with explicit time sources for deterministic lifecycle handling. */
    public SessionManager(SessionStore store, Clock clock, Duration ttl) {
        this.store = store;
        this.clock = clock;
        this.ttl = ttl;
        this.expiryRefreshMargin = ttl.dividedBy(2);
    }

    /** Creates a session with no initial connection. */
    public Session createSession(String sessionId) {
        return createSession(sessionId, SseConnection.noop());
    }

    /** Creates a new persisted generation with the given process-local SSE connection. */
    public Session createSession(String sessionId, SseConnection connection) {
        final var creation = createAndInstall(sessionId, connection);
        final var previous = creation.replaced();
        if (previous != null) {
            previous.close();
            logger.debug("Replaced existing session: {}", sessionId);
        }
        logger.info("Session created: {}", sessionId);
        return creation.created();
    }

    /** Returns the local runtime, reconstructing it from a non-expired snapshot when absent. */
    public Optional<Session> getSession(@Nullable String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        var local = sessions.get(sessionId);
        if (local != null) {
            return Optional.of(local);
        }
        var persisted = store.find(sessionId);
        if (persisted.isEmpty()) {
            return Optional.empty();
        }
        var snapshot = persisted.orElseThrow();
        if (snapshot.state() == SessionState.CLOSED || !snapshot.expiresAt().isAfter(clock.instant())) {
            store.terminate(snapshot.key());
            return Optional.empty();
        }
        final Session hydrated;
        try {
            hydrated = runtime(snapshot, SseConnection.noop());
        } catch (IllegalArgumentException e) {
            logger.warn(
                    "Cannot restore incompatible session snapshot: sessionId={}, protocolVersion={}",
                    sessionId,
                    snapshot.protocolVersion());
            return Optional.empty();
        }
        var winner = sessions.putIfAbsent(sessionId, hydrated);
        if (winner != null) {
            hydrated.close();
            return Optional.of(winner);
        }
        return Optional.of(hydrated);
    }

    /** Returns all process-local runtime sessions. */
    public Collection<Session> allSessions() {
        return sessions.values();
    }

    /** Removes and closes the current generation for the session ID. */
    public void removeSession(String sessionId) {
        var removed = new AtomicReference<Session>();
        sessions.computeIfPresent(sessionId, (id, current) -> {
            removed.set(current);
            return null;
        });
        var local = removed.get();
        if (local != null) {
            store.terminate(local.key());
            local.close();
            logger.info("Session removed: {}", sessionId);
            return;
        }
        store.find(sessionId).ifPresent(snapshot -> store.terminate(snapshot.key()));
    }

    /** Starts the background janitor that closes expired sessions. */
    public void startJanitor(Duration ttl, Duration interval) {
        this.ttl = ttl;
        this.expiryRefreshMargin = ttl.dividedBy(2);
        final var ttlNanos = ttl.toNanos();
        janitor = new AbstractJanitor("session-janitor") {
            @Override
            protected void sweep() {
                SessionManager.this.sweep(ttlNanos);
            }
        };
        janitor.start(interval);
        logger.debug("Session janitor started (interval={}ms, ttl={}ms)", interval.toMillis(), ttlNanos / 1_000_000);
    }

    /** One janitor pass: closes and evicts local sessions that are closed or idle past the TTL. */
    void sweep(long ttlNanos) {
        var now = System.nanoTime();
        for (var session : sessions.values()) {
            try {
                var expired = now - session.lastActivityNanos() > ttlNanos;
                if (session.state() == SessionState.CLOSED || expired) {
                    removeIfCurrent(session);
                }
            } catch (RuntimeException e) {
                logger.warn("Error while sweeping session: {}", session.id(), e);
            }
        }
    }

    private void removeIfCurrent(Session expected) {
        var removed = new boolean[1];
        sessions.computeIfPresent(expected.id(), (id, current) -> {
            if (current == expected) {
                removed[0] = true;
                return null;
            }
            return current;
        });
        if (removed[0]) {
            store.terminate(expected.key());
            expected.close();
        }
    }

    private Session runtime(SessionSnapshot snapshot, SseConnection connection) {
        return Session.fromSnapshot(snapshot, connection, this::persist, this::touch);
    }

    private Creation createAndInstall(String sessionId, SseConnection connection) {
        final var creationLock = acquireCreationLock(sessionId);
        try {
            synchronized (creationLock) {
                final var key = new SessionKey(sessionId, UUID.randomUUID().toString());
                final var snapshot = store.create(key, expiresAt());
                final var created = runtime(snapshot, connection);
                return new Creation(created, sessions.put(sessionId, created));
            }
        } finally {
            releaseCreationLock(sessionId, creationLock);
        }
    }

    private CreationLock acquireCreationLock(String sessionId) {
        return creationLocks.compute(sessionId, (id, current) -> {
            final var lock = current == null ? new CreationLock() : current;
            lock.retain();
            return lock;
        });
    }

    private void releaseCreationLock(String sessionId, CreationLock creationLock) {
        creationLocks.computeIfPresent(sessionId, (id, current) -> {
            if (current != creationLock) {
                return current;
            }
            return creationLock.release() ? null : creationLock;
        });
    }

    private void persist(Session session) {
        if (sessions.get(session.id()) != session) {
            return;
        }
        synchronized (session) {
            if (sessions.get(session.id()) != session) {
                return;
            }
            try {
                final var current = store.find(session.id());
                if (current.isEmpty() || !current.orElseThrow().key().equals(session.key())) {
                    return;
                }
                final var expected = current.orElseThrow();
                final var updated = session.snapshot(expiresAt(), expected.revision() + 1);
                if (store.compareAndSet(expected, updated)) {
                    session.snapshotExpiresAt(updated.expiresAt());
                    return;
                }
                logger.warn("Session snapshot update lost ownership: {}", session.id());
            } catch (RuntimeException e) {
                logger.warn("Failed to persist session snapshot: {}", session.id(), e);
            }
        }
    }

    private void touch(Session session) {
        if (sessions.get(session.id()) != session) {
            return;
        }
        synchronized (session) {
            if (sessions.get(session.id()) != session) {
                return;
            }
            final var now = clock.instant();
            final var refreshAt = session.snapshotExpiresAt().minus(expiryRefreshMargin);
            if (now.isBefore(refreshAt)) {
                return;
            }
            final var updatedExpiry = now.plus(ttl);
            try {
                if (store.touch(session.key(), updatedExpiry)) {
                    session.snapshotExpiresAt(updatedExpiry);
                } else {
                    logger.warn("Session expiry refresh lost ownership: {}", session.id());
                    if (sessions.remove(session.id(), session)) {
                        session.close();
                    }
                }
            } catch (RuntimeException e) {
                logger.warn("Failed to refresh session snapshot expiry: {}", session.id(), e);
            }
        }
    }

    private Instant expiresAt() {
        return clock.instant().plus(ttl);
    }

    @Override
    public void close() {
        try {
            if (janitor != null) {
                janitor.close();
            }
            var local = List.copyOf(sessions.values());
            sessions.clear();
            local.forEach(Session::close);
            store.close();
            logger.debug("SessionManager closed");
        } catch (Exception e) {
            logger.warn("Error while closing SessionManager", e);
        }
    }

    private record Creation(Session created, @Nullable Session replaced) {}

    private static final class CreationLock {
        private int users;

        void retain() {
            users++;
        }

        boolean release() {
            return --users == 0;
        }
    }
}
