/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.server.ServerResourceType;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public class TaskEntry implements ServerResourceType {

    private final TaskDescriptor descriptor;
    private final String id;
    private final @Nullable String sessionId;
    private final AtomicReference<TaskState> status;
    private final long createdAt;
    private final double ttl;
    private volatile long lastUpdatedAt;
    private volatile @Nullable String resultJson;

    public TaskEntry(String name, String id, @Nullable String description) {
        this(TaskDescriptor.builder(name).description(description).build(), id, TaskState.WORKING, 0.0);
    }

    public TaskEntry(String name, String id, @Nullable String description, TaskState status, double ttl) {
        this(TaskDescriptor.builder(name).description(description).build(), id, status, ttl);
    }

    public TaskEntry(TaskDescriptor descriptor, String id, TaskState status, double ttl) {
        this(descriptor, id, status, ttl, null);
    }

    public TaskEntry(TaskDescriptor descriptor, String id, TaskState status, double ttl, @Nullable String sessionId) {
        this.descriptor = descriptor;
        this.id = id;
        this.sessionId = sessionId;
        this.status = new AtomicReference<>(status);
        this.createdAt = System.currentTimeMillis();
        this.lastUpdatedAt = this.createdAt;
        this.ttl = ttl;
    }

    /** The session that created this task, or {@code null} for programmatic/server-global tasks. */
    public @Nullable String sessionId() {
        return sessionId;
    }

    public TaskDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public String name() {
        return descriptor.name();
    }

    public String id() {
        return id;
    }

    @Nullable
    public String description() {
        return descriptor.description();
    }

    public TaskState status() {
        return status.get();
    }

    public long createdAt() {
        return createdAt;
    }

    public long lastUpdatedAt() {
        return lastUpdatedAt;
    }

    public double ttl() {
        return ttl;
    }

    public @Nullable String resultJson() {
        return resultJson;
    }

    public void resultJson(@Nullable String resultJson) {
        this.resultJson = resultJson;
    }

    public boolean transitionTo(TaskState newStatus) {
        return transitionTo(newStatus, null);
    }

    /**
     * Transitions to {@code newStatus}, publishing {@code resultJson} (when non-null) <em>before</em>
     * the new state becomes visible. This ordering guarantees a reader that observes the terminal
     * state via {@link #status()} also observes the result via {@link #resultJson()} — the volatile
     * write happens-before the {@code compareAndSet} that flips the state.
     */
    public boolean transitionTo(TaskState newStatus, @Nullable String resultJson) {
        var current = status.get();
        if (!current.canTransitionTo(newStatus)) {
            return false;
        }
        if (resultJson != null) {
            this.resultJson = resultJson;
        }
        if (status.compareAndSet(current, newStatus)) {
            this.lastUpdatedAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public boolean isExpired() {
        if (ttl <= 0) {
            return false;
        }
        return System.currentTimeMillis() - lastUpdatedAt > (long) (ttl * 1000);
    }

    public String createdAtIso() {
        return Instant.ofEpochMilli(createdAt).toString();
    }

    public String lastUpdatedAtIso() {
        return Instant.ofEpochMilli(lastUpdatedAt).toString();
    }
}
