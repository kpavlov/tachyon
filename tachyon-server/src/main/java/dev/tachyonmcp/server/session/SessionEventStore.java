/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.session;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Persists and replays session events (request/responses, notifications). */
public interface SessionEventStore extends Closeable {
    /** Appends an event to the log. */
    void append(SessionEvent event);

    /**
     * Drains events to the processor one at a time until exhausted or backpressured; returns the
     * last cursor.
     */
    long drain(String sessionId, long cursor, Predicate<SessionEvent> processor);

    /** Returns all events for the session with sequence number greater than {@code lastSeq}. */
    default List<SessionEvent> replay(String sessionId, long lastSeq) {
        var out = new ArrayList<SessionEvent>();
        drain(sessionId, lastSeq, event -> {
            out.add(event);
            return true;
        });
        return List.copyOf(out);
    }
}
