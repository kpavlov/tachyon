/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SessionState;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class McpSession extends Session {

    private final AtomicReference<SseConnection> connection;
    private final AtomicReference<Backpressure> backpressure;
    private final AtomicLong cursor;
    private final Set<String> enabledExtensions = ConcurrentHashMap.newKeySet();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public McpSession(String id, SseConnection connection) {
        super(id);
        this.connection = new AtomicReference<>(Objects.requireNonNull(connection, "connection"));
        this.backpressure = new AtomicReference<>(Backpressure.HOT);
        this.cursor = new AtomicLong(-1);
    }

    public SseConnection connection() {
        return connection.get();
    }

    public void connection(SseConnection connection) {
        this.connection.set(Objects.requireNonNull(connection, "connection"));
        this.lastActivityNanos = System.nanoTime();
    }

    public Backpressure backpressure() {
        return backpressure.get();
    }

    public long cursor() {
        return cursor.get();
    }

    public void cursor(long position) {
        cursor.set(position);
    }

    public void enableExtension(String extensionId) {
        enabledExtensions.add(extensionId);
    }

    public boolean isExtensionEnabled(String extensionId) {
        return enabledExtensions.contains(extensionId);
    }

    public ReadWriteLock lock() {
        return lock;
    }

    public Backpressure computeBackpressure() {
        return backpressure.updateAndGet(current -> {
            if (!connection.get().isWritable()) {
                return Backpressure.COLD;
            }
            return Backpressure.HOT;
        });
    }

    public boolean shouldThrottle() {
        return computeBackpressure() != Backpressure.HOT;
    }

    /**
     * @return {@code true} if the event was delivered; {@code false} if the session is CLOSED
     */
    public boolean send(SseEvent event) {
        var conn = connection.get();
        if (conn == SseConnection.NOOP) {
            return false;
        }
        conn.send(event);
        return true;
    }

    @Override
    public boolean close() {
        lock.writeLock().lock();
        try {
            var closed = state.compareAndSet(SessionState.ACTIVE, SessionState.CLOSED)
                    || state.compareAndSet(SessionState.DRAINING, SessionState.CLOSED)
                    || state.compareAndSet(SessionState.INITIALIZING, SessionState.CLOSED);
            if (closed) {
                var conn = connection.getAndSet(SseConnection.NOOP);
                conn.close();
            }
            return closed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        return "Session[id=" + id() + ", state=" + state.get() + ", bp=" + backpressure.get() + "]";
    }
}
