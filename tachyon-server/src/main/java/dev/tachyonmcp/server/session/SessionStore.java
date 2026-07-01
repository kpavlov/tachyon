/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Storage abstraction for MCP sessions. */
public interface SessionStore extends AutoCloseable {

    /** Stores a session, returning any previous session with the same ID. */
    @Nullable
    McpSession put(String sessionId, McpSession session);

    /** Returns the session for the given ID, if present. */
    Optional<McpSession> get(String sessionId);

    /** Returns the existing session or creates and stores a new one via the factory. */
    McpSession computeIfAbsent(String sessionId, Function<String, McpSession> factory);

    /** Returns all stored sessions. */
    Collection<McpSession> values();

    /** Removes and returns the session for the given ID. */
    @Nullable
    McpSession remove(String sessionId);
}
