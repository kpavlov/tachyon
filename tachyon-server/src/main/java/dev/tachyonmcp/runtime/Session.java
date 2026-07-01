/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/** Base class for sessions identified by a unique string ID with lifecycle state tracking. */
public abstract class Session {

    private final String id;
    protected final AtomicReference<SessionState> state;
    protected volatile long lastActivityNanos;

    protected Session(String id) {
        this.id = Objects.requireNonNull(id, "id");
        this.state = new AtomicReference<>(SessionState.INITIALIZING);
        this.lastActivityNanos = System.nanoTime();
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

    public abstract boolean close();

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
        return "Session[" + "id=" + id + ", state=" + state.get() + "]";
    }
}
