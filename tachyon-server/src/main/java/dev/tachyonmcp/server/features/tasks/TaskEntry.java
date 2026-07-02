/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.server.ServerResourceType;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public class TaskEntry implements ServerResourceType {

    private final TaskDescriptor descriptor;
    private final String id;
    private final AtomicReference<TaskState> status;
    private final long createdAt;
    private final double ttl;
    private volatile long lastUpdatedAt;
    private volatile @Nullable String resultJson;

    public TaskEntry(String name, String id, @Nullable String description) {
        this(TaskDescriptor.of(name, description), id, TaskState.WORKING, 0.0);
    }

    public TaskEntry(String name, String id, @Nullable String description, TaskState status, double ttl) {
        this(TaskDescriptor.of(name, description), id, status, ttl);
    }

    public TaskEntry(TaskDescriptor descriptor, String id, TaskState status, double ttl) {
        this.descriptor = descriptor;
        this.id = id;
        this.status = new AtomicReference<>(status);
        this.createdAt = System.currentTimeMillis();
        this.lastUpdatedAt = this.createdAt;
        this.ttl = ttl;
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
        var current = status.get();
        if (!current.canTransitionTo(newStatus)) {
            return false;
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
