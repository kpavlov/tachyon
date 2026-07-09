/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.runtime.Session;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

@InternalApi
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
    public boolean remove(String sessionId, Session expected) {
        // Not sessions.remove(key, value): that compares by equals, and Session.equals is
        // id-based — it would match (and evict) a replacement instance under the same id.
        // computeIfPresent gives an atomic identity-conditional remove.
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

    @Override
    public void close() {
        sessions.clear();
    }
}
