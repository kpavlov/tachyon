/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * A streamable-HTTP session shared by protocols on this transport. Tracks lifecycle state and
 * the SSE channel (connection, backpressure, replay cursor) for a single client identified by
 * a unique string ID1
 */
public class Session {

    private final String id;
    protected final AtomicReference<SessionState> state;
    protected volatile long lastActivityNanos;

    private final AtomicReference<SseConnection> connection;
    private final AtomicReference<Backpressure> backpressure;
    private final AtomicLong cursor;
    private final Set<String> enabledExtensions = ConcurrentHashMap.newKeySet();

    public Session(String id, SseConnection connection) {
        this.id = Objects.requireNonNull(id, "id");
        this.state = new AtomicReference<>(SessionState.INITIALIZING);
        this.lastActivityNanos = System.nanoTime();
        this.connection = new AtomicReference<>(Objects.requireNonNull(connection, "connection"));
        this.backpressure = new AtomicReference<>(Backpressure.HOT);
        this.cursor = new AtomicLong(-1);
    }

    /** Returns the unique session identifier. */
    public String id() {
        return id;
    }

    /** Returns the current session state. */
    public SessionState state() {
        return state.get();
    }

    /** Returns the nanosecond timestamp of the last activity on this session. */
    public long lastActivityNanos() {
        return lastActivityNanos;
    }

    /** Updates the last-activity timestamp to now. */
    public void touch() {
        this.lastActivityNanos = System.nanoTime();
    }

    /** Transitions from {@link SessionState#INITIALIZING} to {@link SessionState#ACTIVE}. */
    public boolean activate() {
        if (state.compareAndSet(SessionState.INITIALIZING, SessionState.ACTIVE)) {
            this.lastActivityNanos = System.nanoTime();
            return true;
        }
        return false;
    }

    /** Returns the current SSE connection. */
    public SseConnection connection() {
        return connection.get();
    }

    /** Replaces the SSE connection (e.g. after reconnection). */
    public void connection(SseConnection connection) {
        if (state.get() == SessionState.CLOSED) {
            connection.close();
            return;
        }
        this.connection.set(Objects.requireNonNull(connection, "connection"));
        this.lastActivityNanos = System.nanoTime();
    }

    /** Returns the current backpressure state. */
    public Backpressure backpressure() {
        return backpressure.get();
    }

    /** Returns the last replayed SSE event cursor. */
    public long cursor() {
        return cursor.get();
    }

    /** Sets the replayed SSE event cursor. */
    public void cursor(long position) {
        cursor.set(position);
    }

    /** Enables an extension for this session. */
    public void enableExtension(String extensionId) {
        enabledExtensions.add(extensionId);
    }

    /** Returns whether the given extension is enabled for this session. */
    public boolean isExtensionEnabled(String extensionId) {
        return enabledExtensions.contains(extensionId);
    }

    /** Recomputes and returns the backpressure state based on stream writability. */
    public Backpressure computeBackpressure() {
        return backpressure.updateAndGet(current -> {
            if (!connection.get().isWritable()) {
                return Backpressure.COLD;
            }
            return Backpressure.HOT;
        });
    }

    /** Returns {@code true} if the session should throttle outbound events. */
    public boolean shouldThrottle() {
        return computeBackpressure() != Backpressure.HOT;
    }

    /**
     * Sends an SSE event to the client. Returns {@code false} without sending when no connection
     * is attached or the stream is throttled ({@link Backpressure#COLD}) — a slow client must not
     * accumulate events in the channel's outbound buffer. Dropped events stay in the event log and
     * are replayable via {@code Last-Event-ID} on reconnect.
     */
    public boolean send(SseEvent event) {
        var conn = connection.get();
        if (conn == SseConnection.NOOP) {
            return false;
        }
        if (shouldThrottle()) {
            return false;
        }
        conn.send(event);
        return true;
    }

    /** Closes the session and its underlying connection. Returns {@code true} if this call closed it. */
    public boolean close() {
        var closed = state.compareAndSet(SessionState.ACTIVE, SessionState.CLOSED)
                || state.compareAndSet(SessionState.DRAINING, SessionState.CLOSED)
                || state.compareAndSet(SessionState.INITIALIZING, SessionState.CLOSED);
        if (closed) {
            var conn = connection.getAndSet(SseConnection.NOOP);
            conn.close();
        }
        return closed;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null || getClass() != obj.getClass()) return false;
        var that = (Session) obj;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Session[id=" + id + ", state=" + state.get() + ", bp=" + backpressure.get() + "]";
    }
}
