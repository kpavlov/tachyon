/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.session;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.core.runtime.Session;
import dev.tachyonmcp.core.runtime.SessionState;
import dev.tachyonmcp.core.runtime.SseConnection;
import dev.tachyonmcp.core.server.internal.AbstractJanitor;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages the lifecycle of MCP sessions. */
@InternalApi
public class SessionManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    /**
     * Session ids explicitly torn down (client DELETE, or an abrupt close during the init
     * handshake) — never resurrected by {@link #getOrResumeSession}, unlike a TTL-expired
     * session, which is meant to be resumable. Grows for the process lifetime; a session id
     * removed this way is never forgotten. Acceptable for now — explicit termination is rare
     * relative to normal traffic — but worth bounding if that stops being true.
     */
    private final Set<String> explicitlyClosed = ConcurrentHashMap.newKeySet();

    private final @Nullable Consumer<String> onSessionClosed;
    private @Nullable AbstractJanitor janitor;

    public SessionManager() {
        this(null);
    }

    /** Creates a session manager that invokes {@code onSessionClosed} whenever a session is removed. */
    public SessionManager(@Nullable Consumer<String> onSessionClosed) {
        this.onSessionClosed = onSessionClosed;
    }

    /** Creates a session with no initial connection. */
    public Session createSession(String sessionId) {
        return createSession(sessionId, SseConnection.noop());
    }

    /** Creates a session with the given SSE connection. */
    public Session createSession(String sessionId, SseConnection connection) {
        var session = new Session(sessionId, connection);
        var previous = sessions.put(sessionId, session);
        if (previous != null) {
            logger.debug("Replaced existing session: {}", sessionId);
            previous.close();
        }
        logger.info("Session created: {}", sessionId);
        return session;
    }

    /** Returns the session for the given ID, if present. */
    public Optional<Session> getSession(@Nullable String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * Returns the live session for {@code sessionId} if present; otherwise, unless it was
     * explicitly closed, atomically creates and returns one if {@code hasHistory} confirms a
     * resumable event history exists. {@code hasHistory} is only invoked on a live-table miss —
     * it may be expensive (a durable store lookup). The check-and-create is atomic per id, so
     * concurrent callers for the same id observe the same instance; none replaces or closes an
     * already-created session.
     */
    public Optional<Session> getOrResumeSession(String sessionId, BooleanSupplier hasHistory) {
        var existing = sessions.get(sessionId);
        if (existing != null) {
            return Optional.of(existing);
        }
        if (explicitlyClosed.contains(sessionId) || !hasHistory.getAsBoolean()) {
            return Optional.empty();
        }
        return Optional.of(sessions.computeIfAbsent(sessionId, id -> {
            logger.info("Session recognized from history and recreated: {}", id);
            return new Session(id, SseConnection.noop());
        }));
    }

    /** Returns all active sessions. */
    public Collection<Session> allSessions() {
        return sessions.values();
    }

    /**
     * Removes and closes the session with the given ID, marking it so
     * {@link #getOrResumeSession} never resurrects it — unlike a TTL-expired session (see
     * {@link #sweep}), an explicitly closed one is done for good.
     */
    public void removeSession(String sessionId) {
        // Written before the live-map removal so a concurrent getOrResumeSession racing this
        // call either still sees the live session (fine) or already sees the tombstone.
        explicitlyClosed.add(sessionId);
        var session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
            logger.info("Session removed: {}", sessionId);
            notifySessionClosed(sessionId);
        }
    }

    /** Starts the background janitor that closes expired sessions. */
    public void startJanitor(Duration ttl, Duration interval) {
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

    /** One janitor pass: closes and evicts sessions that are CLOSED or idle beyond the TTL. */
    void sweep(long ttlNanos) {
        long now = System.nanoTime();
        for (var session : sessions.values()) {
            try {
                // Elapsed-based comparison, not `lastActivity < now - ttl`: the latter breaks
                // across nanoTime's sign wraparound.
                var expired = now - session.lastActivityNanos() > ttlNanos;
                if (session.state() == SessionState.CLOSED || expired) {
                    session.close();
                    if (removeIfCurrent(session.id(), session)) {
                        logger.debug("Janitor removed session: {}", session.id());
                        notifySessionClosed(session.id());
                    }
                }
            } catch (Exception e) {
                logger.warn("Error while sweeping session: {}", session.id(), e);
            }
        }
    }

    /**
     * Removes {@code expected} from the live table only if it is still the current instance,
     * returning whether it was removed. Used by expiry sweeps so a replacement session created
     * under the same id (custom SessionIdGenerator) between the expiry check and the removal is
     * never evicted. Not {@code sessions.remove(key, value)}: that compares by equals, and
     * {@code Session.equals} is id-based — it would match (and evict) a replacement instance
     * under the same id. {@code computeIfPresent} gives an atomic identity-conditional remove.
     */
    boolean removeIfCurrent(String sessionId, Session expected) {
        var removed = new boolean[1];
        sessions.computeIfPresent(sessionId, (id, current) -> {
            if (current == expected) {
                removed[0] = true;
                return null;
            }
            return current;
        });
        return removed[0];
    }

    private void notifySessionClosed(String sessionId) {
        if (onSessionClosed == null) {
            return;
        }
        try {
            onSessionClosed.accept(sessionId);
        } catch (Exception e) {
            logger.warn("onSessionClosed listener threw for session: {}", sessionId, e);
        }
    }

    public void close() {
        try {
            if (janitor != null) {
                janitor.close();
            }
            sessions.values().forEach(Session::close);
            logger.debug("SessionManager closed");
        } catch (Exception e) {
            logger.warn("Error while closing SessionManager", e);
        }
    }
}
