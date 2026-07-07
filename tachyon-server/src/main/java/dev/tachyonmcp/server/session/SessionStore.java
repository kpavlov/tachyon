/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.runtime.Session;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Storage abstraction for MCP sessions. */
public interface SessionStore extends AutoCloseable {

    /** Stores a session, returning any previous session with the same ID. */
    @Nullable
    Session put(String sessionId, Session session);

    /** Returns the session for the given ID, if present. */
    Optional<Session> get(String sessionId);

    /** Returns the existing session or creates and stores a new one via the factory. */
    Session computeIfAbsent(String sessionId, Function<String, Session> factory);

    /** Returns all stored sessions. */
    Collection<Session> values();

    /** Removes and returns the session for the given ID. */
    @Nullable
    Session remove(String sessionId);

    /**
     * Removes the session for the given ID only if the stored instance is {@code expected},
     * returning whether it was removed. Used by expiry sweeps so a replacement session created
     * under the same ID between the expiry check and the removal is never evicted. This default
     * is check-then-act; implementations backed by an atomic store should override it (see
     * {@code InMemorySessionStore}).
     */
    default boolean remove(String sessionId, Session expected) {
        if (get(sessionId).orElse(null) == expected) {
            remove(sessionId);
            return true;
        }
        return false;
    }
}
