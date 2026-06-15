/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();

    @Override
    public @Nullable McpSession put(String sessionId, McpSession session) {
        return sessions.put(sessionId, session);
    }

    @Override
    public Optional<McpSession> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public McpSession computeIfAbsent(String sessionId, Function<String, McpSession> factory) {
        return sessions.computeIfAbsent(sessionId, factory);
    }

    @Override
    public Collection<McpSession> values() {
        return sessions.values();
    }

    @Override
    public @Nullable McpSession remove(String sessionId) {
        return sessions.remove(sessionId);
    }

    @Override
    public void close() {
        sessions.clear();
    }
}
