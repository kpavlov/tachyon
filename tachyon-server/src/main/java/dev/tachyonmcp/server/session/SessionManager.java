/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SessionState;
import dev.tachyonmcp.runtime.SseConnection;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages the lifecycle of MCP sessions. */
public class SessionManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final SessionStore store;
    private final ScheduledExecutorService janitor;

    /** Creates a session manager backed by the given store. */
    public SessionManager(SessionStore store) {
        this.store = store;
        this.janitor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "session-janitor");
            t.setDaemon(true);
            return t;
        });
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
    public void startJanitor(Duration ttl) {
        final var ttlNanos = ttl.toNanos();
        janitor.scheduleWithFixedDelay(
                () -> {
                    long deadline = System.nanoTime() - ttlNanos;
                    for (var session : store.values()) {
                        if (session.state() == SessionState.CLOSED || session.lastActivityNanos() < deadline) {
                            session.close();
                            store.remove(session.id());
                            logger.debug("Janitor removed session: {}", session.id());
                        }
                    }
                },
                5,
                5,
                TimeUnit.SECONDS);
        logger.debug("Session janitor started (interval=5s, ttl={}ms)", ttlNanos / 1_000_000);
    }

    public void close() {
        try {
            janitor.shutdownNow();
            store.values().forEach(Session::close);
            store.close();
            logger.debug("SessionManager closed");
        } catch (Exception e) {
            logger.warn("Error while closing SessionManager", e);
        }
    }
}
