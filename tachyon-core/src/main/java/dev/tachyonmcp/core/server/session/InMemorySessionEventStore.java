/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.session;

import dev.tachyonmcp.api.annotations.InternalApi;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * In-memory event log bounded by {@link #maxEvents} total and {@link #maxEventsPerSession} per
 * session — each session has its own FIFO deque, so one busy session can only evict its own
 * oldest entries, never another's. {@code headIndex} tracks each session's oldest surviving
 * index, so finding the globally-oldest entry to evict is O(log sessions), not a full scan.
 * Dropped entries are unrecoverable, matching typical broker retention semantics.
 *
 * <p>{@link #drain} cursors are global append indices from one shared counter, stable across
 * trims. Reads snapshot a session's window under the lock and process outside it, so a slow
 * consumer never blocks appends.
 *
 * <p>Guarded by a {@link ReentrantLock}, not {@code synchronized}: appends run on virtual
 * threads, and a VT blocked on a monitor pins its carrier (Java 21; fixed in JEP 491 / Java 24).
 * A j.u.c lock parks the VT instead.
 */
@InternalApi
public final class InMemorySessionEventStore implements SessionEventStore {

    static final int DEFAULT_MAX_EVENTS = 10_000;
    static final int DEFAULT_MAX_SESSION_EVENTS = 512;
    /**
     * The maximum number of session events that can be retained in memory, summed across all
     * sessions, for replay or processing. If the total exceeds this limit, the globally oldest
     * live event (from whichever session) is discarded to make room for new ones.
     */
    final int maxEvents;
    /**
     * The maximum number of session events that can be retained in memory for a single session.
     * If a session's own count exceeds this limit, that session's own oldest event is discarded,
     * regardless of the global cap. Default is ({@link #DEFAULT_MAX_SESSION_EVENTS}).
     */
    final int maxEventsPerSession;

    private final ReentrantLock lock = new ReentrantLock();
    /**
     * Guarded by {@link #lock}
     */
    private long nextIndex;

    /**
     * Guarded by {@link #lock}
     */
    private int totalLive;

    private final Map<String, ArrayDeque<IndexedEvent>> bySession = new HashMap<>();

    /**
     * Each session's current oldest surviving index → sessionId, so the globally-oldest live
     * event can be found in O(log sessions) instead of scanning every session. Guarded by
     * {@link #lock}
     */
    private final NavigableMap<Long, String> headIndex = new TreeMap<>();

    private record IndexedEvent(long index, SessionEvent event) {}

    public InMemorySessionEventStore() {
        this(0, DEFAULT_MAX_EVENTS, DEFAULT_MAX_SESSION_EVENTS);
    }

    public InMemorySessionEventStore(long firstIndex, int maxEvents) {
        this(firstIndex, maxEvents, DEFAULT_MAX_SESSION_EVENTS);
    }

    public InMemorySessionEventStore(long firstIndex, int maxEvents, int maxEventsPerSession) {
        this.nextIndex = firstIndex;
        this.maxEvents = maxEvents;
        this.maxEventsPerSession = maxEventsPerSession;
    }

    @Override
    public void append(SessionEvent event) {
        lock.lock();
        try {
            var deque = bySession.computeIfAbsent(event.sessionId(), k -> new ArrayDeque<>());
            boolean wasEmpty = deque.isEmpty();
            var indexed = new IndexedEvent(nextIndex++, event);
            deque.addLast(indexed);
            totalLive++;
            if (wasEmpty) {
                headIndex.put(indexed.index(), event.sessionId());
            }

            if (deque.size() > maxEventsPerSession) {
                evict(event.sessionId(), deque);
            }
            if (totalLive > maxEvents) {
                evictGlobalOldest();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Guarded by {@link #lock}
     */
    private void evict(String sessionId, ArrayDeque<IndexedEvent> deque) {
        var removed = deque.removeFirst();
        headIndex.remove(removed.index());
        totalLive--;
        if (deque.isEmpty()) {
            bySession.remove(sessionId);
        } else {
            headIndex.put(deque.peekFirst().index(), sessionId);
        }
    }

    /**
     * Guarded by {@link #lock}
     */
    private void evictGlobalOldest() {
        var oldest = headIndex.firstEntry();
        if (oldest != null) {
            evict(oldest.getValue(), bySession.get(oldest.getValue()));
        }
    }

    private List<IndexedEvent> snapshot(String sessionId) {
        lock.lock();
        try {
            var deque = bySession.get(sessionId);
            return deque == null ? List.of() : List.copyOf(deque);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long drain(String sessionId, long cursor, Predicate<SessionEvent> processor) {
        var snapshot = snapshot(sessionId);
        long lastIndex = cursor;
        for (var indexed : snapshot) {
            if (indexed.index() <= cursor) {
                continue;
            }
            lastIndex = indexed.index();
            if (!processor.test(indexed.event())) {
                break;
            }
        }
        return lastIndex;
    }

    @Override
    public void close() {
        lock.lock();
        try {
            bySession.clear();
            headIndex.clear();
            totalLive = 0;
        } finally {
            lock.unlock();
        }
    }
}
