/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.session;

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

    /**
     * Returns {@code true} if any event has ever been recorded for {@code sessionId}. Used to
     * recognize a resumable session when no live record of it exists in this process (e.g. after
     * a restart).
     *
     * <p>The default stops after the first matching event via {@link #drain}. Implementations
     * that can answer existence more directly should override this instead.
     */
    default boolean exists(String sessionId) {
        var found = new boolean[1];
        drain(sessionId, -1, event -> {
            found[0] = true;
            return false;
        });
        return found[0];
    }

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
