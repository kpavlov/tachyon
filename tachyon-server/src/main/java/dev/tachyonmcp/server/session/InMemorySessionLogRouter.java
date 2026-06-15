/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

public final class InMemorySessionLogRouter implements SessionLogRouter {

    private final ConcurrentLinkedQueue<SessionEvent> events = new ConcurrentLinkedQueue<>();

    @Override
    public void append(SessionEvent event) {
        events.add(event);
    }

    @Override
    public List<SessionEvent> replay(String sessionId, long lastSeq) {
        var snap = events.toArray();
        var result = new ArrayList<SessionEvent>();
        int start = lastSeq < 0 ? 0 : (int) Math.min(lastSeq, snap.length);
        for (int i = start; i < snap.length; i++) {
            var event = (SessionEvent) snap[i];
            if (event.sessionId().equals(sessionId)) {
                result.add(event);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public long pump(String sessionId, long cursor, Predicate<SessionEvent> processor) {
        var snap = events.toArray();
        int start = cursor < 0 ? 0 : (int) Math.min(cursor, snap.length);
        long lastIndex = cursor < 0 ? -1 : cursor;
        for (int i = start; i < snap.length; i++) {
            var event = (SessionEvent) snap[i];
            lastIndex = i;
            if (!event.sessionId().equals(sessionId)) {
                continue;
            }
            if (!processor.test(event)) {
                break;
            }
        }
        return lastIndex;
    }

    @Override
    public void close() {
        events.clear();
    }
}
