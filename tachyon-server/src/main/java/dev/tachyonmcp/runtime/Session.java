/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public abstract class Session {

    private final String id;
    protected final AtomicReference<SessionState> state;
    protected volatile long lastActivityNanos;

    protected Session(String id) {
        this.id = Objects.requireNonNull(id, "id");
        this.state = new AtomicReference<>(SessionState.INITIALIZING);
        this.lastActivityNanos = System.nanoTime();
    }

    public String id() {
        return id;
    }

    public SessionState state() {
        return state.get();
    }

    public long lastActivityNanos() {
        return lastActivityNanos;
    }

    public void touch() {
        this.lastActivityNanos = System.nanoTime();
    }

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
