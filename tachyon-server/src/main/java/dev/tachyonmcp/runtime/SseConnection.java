/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import dev.tachyonmcp.annotations.InternalApi;

/** A writable SSE connection to a client. */
@InternalApi
public interface SseConnection {

    /** Returns {@code true} if the underlying channel is writable. */
    boolean isWritable();

    /** Sends an SSE event to the client. */
    void send(SseEvent event);

    /** Closes the connection. Idempotent. */
    default void close() {}

    SseConnection NOOP = new SseConnection() {
        @Override
        public boolean isWritable() {
            return false;
        }

        @Override
        public void send(SseEvent event) {}
    };
}
