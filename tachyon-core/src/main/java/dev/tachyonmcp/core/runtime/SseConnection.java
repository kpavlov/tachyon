/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.runtime;

import dev.tachyonmcp.api.annotations.InternalApi;

/** A writable SSE connection to a client. */
@InternalApi
public interface SseConnection {

    /** Returns {@code true} if the underlying channel is writable. */
    boolean isWritable();

    /** Sends an SSE event to the client. */
    void send(SseEvent event);

    /** Closes the connection. Idempotent. */
    default void close() {}

    /** Returns the no-op connection singleton: not writable, drops every sent event. */
    static SseConnection noop() {
        return NoopSseConnection.INSTANCE;
    }
}

final class NoopSseConnection implements SseConnection {

    static final SseConnection INSTANCE = new NoopSseConnection();

    private NoopSseConnection() {}

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public void send(SseEvent event) {}
}
