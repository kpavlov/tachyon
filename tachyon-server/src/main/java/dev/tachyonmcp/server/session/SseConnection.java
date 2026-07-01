/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

/** A writable SSE connection to a client. */
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
