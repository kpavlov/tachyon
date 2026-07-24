/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SessionState;
import dev.tachyonmcp.runtime.SseConnection;
import dev.tachyonmcp.server.internal.AbstractJanitor;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages the lifecycle of MCP sessions. */
@InternalApi
public class SessionManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final SessionStore store;
    private @Nullable AbstractJanitor janitor;

    /** Creates a session manager backed by the given store. */
    public SessionManager(SessionStore store) {
        this.store = store;
    }

    /** Creates a session with no initial connection. */
    public Session createSession(String sessionId) {
        return createSession(sessionId, SseConnection.NOOP);
    }

    /** Creates a session with the given SSE connection. */
    public Session createSession(String sessionId, SseConnection connection) {
        var session = new Session(sessionId, connection);
        var previous = store.put(sessionId, session);
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
        return store.get(sessionId);
    }

    /** Returns all active sessions. */
    public Collection<Session> allSessions() {
        return store.values();
    }

    /** Removes and closes the session with the given ID. */
    public void removeSession(String sessionId) {
        var session = store.remove(sessionId);
        if (session != null) {
            session.close();
            logger.info("Session removed: {}", sessionId);
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
        for (var session : store.values()) {
            try {
                // Elapsed-based comparison, not `lastActivity < now - ttl`: the latter breaks
                // across nanoTime's sign wraparound.
                var expired = now - session.lastActivityNanos() > ttlNanos;
                if (session.state() == SessionState.CLOSED || expired) {
                    session.close();
                    // Atomic conditional remove: a replacement session created under the
                    // same id (custom SessionIdGenerator) must never be evicted.
                    if (store.remove(session.id(), session)) {
                        logger.debug("Janitor removed session: {}", session.id());
                    }
                }
            } catch (Exception e) {
                logger.warn("Error while sweeping session: {}", session.id(), e);
            }
        }
    }

    public void close() {
        try {
            if (janitor != null) {
                janitor.close();
            }
            store.values().forEach(Session::close);
            store.close();
            logger.debug("SessionManager closed");
        } catch (Exception e) {
            logger.warn("Error while closing SessionManager", e);
        }
    }
}
