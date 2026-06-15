/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

public interface SseConnection {

    boolean isWritable();

    void send(SseEvent event);

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
