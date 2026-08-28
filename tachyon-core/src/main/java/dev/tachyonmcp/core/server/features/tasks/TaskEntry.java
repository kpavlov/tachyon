/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.domain.ProgressToken;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/** Cached task projection plus server-local retention and notification ownership. */
@InternalApi
final class TaskEntry {

    private volatile TaskSnapshot snapshot;
    private final @Nullable String sessionId;
    private final @Nullable ProgressToken progressToken;
    private final Duration keepAlive;
    private final Clock clock;
    private volatile Instant cachedAt;
    private final ReentrantLock lock = new ReentrantLock();

    TaskEntry(
            TaskSnapshot snapshot,
            @Nullable String sessionId,
            @Nullable ProgressToken progressToken,
            Duration keepAlive,
            Clock clock) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.sessionId = sessionId;
        this.progressToken = progressToken;
        this.keepAlive = Objects.requireNonNull(keepAlive, "keepAlive");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cachedAt = clock.instant();
    }

    TaskSnapshot publish(TaskSnapshot candidate) {
        lock.lock();
        try {
            if (!snapshot.taskId().equals(candidate.taskId())) {
                throw new IllegalArgumentException("Task ID cannot change");
            }
            if (candidate.revision() > snapshot.revision()) {
                cachedAt = clock.instant();
                snapshot = candidate;
            }
            return snapshot;
        } finally {
            lock.unlock();
        }
    }

    TaskSnapshot snapshot() {
        return snapshot;
    }

    String id() {
        return snapshot.taskId();
    }

    @Nullable
    String sessionId() {
        return sessionId;
    }

    @Nullable
    ProgressToken progressToken() {
        return progressToken;
    }

    boolean isResultExpired() {
        return snapshot.status().isTerminal()
                && keepAlive.compareTo(Duration.ZERO) > 0
                && !clock.instant().isBefore(cachedAt.plus(keepAlive));
    }
}
