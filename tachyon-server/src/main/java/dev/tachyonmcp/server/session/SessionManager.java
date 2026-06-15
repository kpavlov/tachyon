/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.runtime.SessionState;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final SessionStore store;
    private final ScheduledExecutorService janitor;

    public SessionManager(SessionStore store) {
        this.store = store;
        this.janitor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "session-janitor");
            t.setDaemon(true);
            return t;
        });
    }

    public McpSession createSession(String sessionId) {
        return createSession(sessionId, SseConnection.NOOP);
    }

    public McpSession createSession(String sessionId, SseConnection connection) {
        var session = new McpSession(sessionId, connection);
        var previous = store.put(sessionId, session);
        if (previous != null) {
            logger.warn("Replaced existing session: {}", sessionId);
            previous.close();
        }
        logger.info("Session created: {}", sessionId);
        return session;
    }

    public Optional<McpSession> getSession(@Nullable String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return store.get(sessionId);
    }

    public Collection<McpSession> allSessions() {
        return store.values();
    }

    public void removeSession(String sessionId) {
        var session = store.remove(sessionId);
        if (session != null) {
            session.close();
            logger.info("Session removed: {}", sessionId);
        }
    }

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
        logger.info("Session janitor started (interval=5s, ttl={}ms)", ttlNanos / 1_000_000);
    }

    public void close() {
        try {
            janitor.shutdownNow();
            store.values().forEach(McpSession::close);
            store.close();
            logger.info("SessionManager closed");
        } catch (Exception e) {
            logger.error("Error while closing SessionManager", e);
        }
    }
}
