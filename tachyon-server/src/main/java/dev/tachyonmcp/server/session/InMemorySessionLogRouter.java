/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * In-memory event log bounded to {@link #maxEvents}: appends past the cap drop the oldest entry
 * (they remain unrecoverable — an SSE client reconnecting with a {@code Last-Event-ID} older than
 * the window simply misses those events, matching typical broker retention semantics).
 *
 * <p>Cursors handed out by {@link #pump} are <em>global</em> append indices, stable across trims
 * via the {@code firstIndex} offset. Reads snapshot the window under the lock and process outside
 * it, so a slow consumer never blocks appends.
 *
 * <p>Guarded by a {@link ReentrantLock}, not {@code synchronized}: appends run on virtual threads,
 * and on Java 21 a VT contending on a monitor pins its carrier thread (fixed only in JEP 491 /
 * Java 24). A j.u.c lock parks the virtual thread instead and frees the carrier.
 */
public final class InMemorySessionLogRouter implements SessionLogRouter {

    static final int DEFAULT_MAX_EVENTS = 10_000;
    /**
     * The maximum number of session events that can be retained in memory for replay or processing.
     * This variable determines the upper limit on how many events the in-memory session log
     * can hold at any given time. If the number of events exceeds this limit, older events may
     * be discarded to make room for new ones.
     */
    final int maxEvents;

    private final ReentrantLock lock = new ReentrantLock();
    /**
     * Guarded by {@link #lock}
     */
    private long firstIndex;

    private final ArrayDeque<SessionEvent> events = new ArrayDeque<>();

    public InMemorySessionLogRouter() {
        this(0, DEFAULT_MAX_EVENTS);
    }

    public InMemorySessionLogRouter(long firstIndex, int maxEvents) {
        this.firstIndex = firstIndex;
        this.maxEvents = maxEvents;
    }

    @Override
    public void append(SessionEvent event) {
        lock.lock();
        try {
            events.addLast(event);
            if (events.size() > maxEvents) {
                events.removeFirst();
                firstIndex++;
            }
        } finally {
            lock.unlock();
        }
    }

    private Snapshot snapshot() {
        lock.lock();
        try {
            return new Snapshot(events.toArray(new SessionEvent[0]), firstIndex);
        } finally {
            lock.unlock();
        }
    }

    private record Snapshot(SessionEvent[] events, long firstIndex) {}

    @Override
    public List<SessionEvent> replay(String sessionId, long lastSeq) {
        var snap = snapshot();
        var result = new ArrayList<SessionEvent>();
        // Contract: events with sequence number GREATER THAN lastSeq — resume after it, like pump.
        int start = lastSeq < 0 ? 0 : Math.clamp(lastSeq + 1 - snap.firstIndex(), 0, snap.events().length);
        for (int i = start; i < snap.events().length; i++) {
            var event = snap.events()[i];
            if (event.sessionId().equals(sessionId)) {
                result.add(event);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public long pump(String sessionId, long cursor, Predicate<SessionEvent> processor) {
        var snap = snapshot();
        // cursor is the last global index already processed; resume at the next one.
        long next = cursor < 0 ? snap.firstIndex() : cursor + 1;
        int start = Math.clamp(next - snap.firstIndex(), 0, snap.events().length);
        long lastIndex = cursor;
        for (int i = start; i < snap.events().length; i++) {
            var event = snap.events()[i];
            lastIndex = snap.firstIndex() + i;
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
        lock.lock();
        try {
            events.clear();
        } finally {
            lock.unlock();
        }
    }
}
