/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.runtime.Session;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public @Nullable Session put(String sessionId, Session session) {
        return sessions.put(sessionId, session);
    }

    @Override
    public Optional<Session> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public Session computeIfAbsent(String sessionId, Function<String, Session> factory) {
        return sessions.computeIfAbsent(sessionId, factory);
    }

    @Override
    public Collection<Session> values() {
        return sessions.values();
    }

    @Override
    public @Nullable Session remove(String sessionId) {
        return sessions.remove(sessionId);
    }

    @Override
    public void close() {
        sessions.clear();
    }
}
