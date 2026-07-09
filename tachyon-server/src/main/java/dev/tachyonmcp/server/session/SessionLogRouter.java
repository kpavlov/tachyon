/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.session;

import java.io.Closeable;
import java.util.List;
import java.util.function.Predicate;

/** Persists and replays session events (request/responses, notifications). */
public interface SessionLogRouter extends Closeable {
    /** Appends an event to the log. */
    void append(SessionEvent event);

    /** Returns all events for the session with sequence number greater than {@code lastSeq}. */
    List<SessionEvent> replay(String sessionId, long lastSeq);

    /** Pumps events to the processor, one at a time, until exhausted and returns the last cursor. */
    long pump(String sessionId, long cursor, Predicate<SessionEvent> processor);
}
