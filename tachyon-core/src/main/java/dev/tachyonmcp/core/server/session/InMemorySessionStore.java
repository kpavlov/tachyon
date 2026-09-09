/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.session;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.core.runtime.SessionState;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Default process-local session snapshot store. */
@InternalApi
public final class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, SessionSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public SessionSnapshot create(SessionKey key, Instant expiresAt) {
        final var snapshot = new SessionSnapshot(key, SessionState.INITIALIZING, null, Set.of(), null, expiresAt, 0);
        snapshots.put(key.sessionId(), snapshot);
        return snapshot;
    }

    @Override
    public Optional<SessionSnapshot> find(String sessionId) {
        return Optional.ofNullable(snapshots.get(sessionId));
    }

    @Override
    public boolean compareAndSet(SessionSnapshot expected, SessionSnapshot updated) {
        if (!expected.key().equals(updated.key()) || updated.revision() <= expected.revision()) {
            return false;
        }
        return snapshots.replace(expected.key().sessionId(), expected, updated);
    }

    @Override
    public boolean touch(SessionKey key, Instant expiresAt) {
        final var touched = new boolean[1];
        snapshots.computeIfPresent(key.sessionId(), (sessionId, current) -> {
            if (!current.key().equals(key)) {
                return current;
            }
            touched[0] = true;
            return new SessionSnapshot(
                    current.key(),
                    current.state(),
                    current.protocolVersion(),
                    current.enabledExtensionIds(),
                    current.loggingLevel(),
                    expiresAt,
                    current.revision() + 1);
        });
        return touched[0];
    }

    @Override
    public boolean terminate(SessionKey key) {
        final var removed = new boolean[1];
        snapshots.computeIfPresent(key.sessionId(), (sessionId, current) -> {
            if (current.key().equals(key)) {
                removed[0] = true;
                return null;
            }
            return current;
        });
        return removed[0];
    }

    @Override
    public void close() {
        snapshots.clear();
    }
}
