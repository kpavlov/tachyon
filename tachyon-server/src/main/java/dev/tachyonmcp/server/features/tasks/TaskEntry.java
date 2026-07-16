/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.server.ServerFeature;
import dev.tachyonmcp.server.domain.ContentBlock;
import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.Task;
import dev.tachyonmcp.server.domain.TaskResult;
import dev.tachyonmcp.server.domain.TextContent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@InternalApi
public class TaskEntry implements ServerFeature<TaskDescriptor>, Task {

    private final TaskDescriptor descriptor;
    private final String id;
    private final @Nullable String sessionId;
    private final @Nullable Map<String, JsonNode> meta;
    private final AtomicReference<TaskState> status;
    private final long createdAt;
    private final @Nullable Duration ttl;
    private volatile long lastUpdatedAt;
    private volatile @Nullable String statusMessage;
    private volatile @Nullable TaskResult result;
    private final CompletableFuture<TaskResult> completionFuture = new CompletableFuture<>();
    private final @Nullable Object progressToken;

    public TaskEntry(String id, @Nullable String description) {
        this(TaskDescriptor.builder().id(id).build(), id, TaskState.WORKING, null, null, null, null);
    }

    public TaskEntry(TaskDescriptor descriptor, String id, TaskState status, double ttl) {
        this(descriptor, id, status, ttl > 0 ? Duration.ofSeconds((long) ttl) : null, null, null, null);
    }

    public TaskEntry(TaskDescriptor descriptor, String id, TaskState status, double ttl, @Nullable String sessionId) {
        this(descriptor, id, status, ttl > 0 ? Duration.ofSeconds((long) ttl) : null, sessionId, null, null);
    }

    public TaskEntry(
            TaskDescriptor descriptor,
            String id,
            TaskState status,
            @Nullable Duration ttl,
            @Nullable String sessionId,
            @Nullable Object progressToken) {
        this(descriptor, id, status, ttl, sessionId, progressToken, null);
    }

    public TaskEntry(
            TaskDescriptor descriptor,
            String id,
            TaskState status,
            @Nullable Duration ttl,
            @Nullable String sessionId,
            @Nullable Object progressToken,
            @Nullable Map<String, JsonNode> meta) {
        this.descriptor = descriptor;
        this.id = id;
        this.sessionId = sessionId;
        this.meta = meta;
        this.status = new AtomicReference<>(status);
        this.createdAt = System.currentTimeMillis();
        this.lastUpdatedAt = this.createdAt;
        this.ttl = ttl;
        this.progressToken = progressToken;
    }

    /** The session that created this task, or {@code null} for programmatic/server-global tasks. */
    public @Nullable String sessionId() {
        return sessionId;
    }

    public @Nullable Object progressToken() {
        return progressToken;
    }

    @Override
    public @Nullable Map<String, JsonNode> meta() {
        return meta;
    }

    @Override
    public TaskDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public String name() {
        return id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public TaskState status() {
        return status.get();
    }

    @Override
    public @Nullable String statusMessage() {
        return statusMessage;
    }

    @Override
    public Instant createdAt() {
        return Instant.ofEpochMilli(createdAt);
    }

    @Override
    public @Nullable Duration ttl() {
        return ttl;
    }

    @Override
    public @Nullable TaskResult result() {
        return result;
    }

    @Override
    public CompletionStage<TaskResult> completion() {
        return completionFuture;
    }

    @Override
    public boolean complete(TaskResult.Completed result) {
        return transitionTo(TaskState.COMPLETED, (TaskResult) result);
    }

    @Override
    public boolean fail(TaskResult.Failed result) {
        return transitionTo(TaskState.FAILED, (TaskResult) result);
    }

    @Override
    public boolean cancel(@Nullable String statusMessage) {
        if (transitionTo(TaskState.CANCELLED)) {
            if (statusMessage != null) {
                this.statusMessage = statusMessage;
            }
            completionFuture.completeExceptionally(
                    new IllegalStateException("Task cancelled" + (statusMessage != null ? ": " + statusMessage : "")));
            return true;
        }
        return false;
    }

    @Override
    public boolean requireInput(InputRequest request, @Nullable String statusMessage) {
        if (transitionTo(TaskState.INPUT_REQUIRED)) {
            if (statusMessage != null) {
                this.statusMessage = statusMessage;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean resume(@Nullable String statusMessage) {
        if (transitionTo(TaskState.WORKING)) {
            if (statusMessage != null) {
                this.statusMessage = statusMessage;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean updateMessage(String statusMessage) {
        Objects.requireNonNull(statusMessage, "statusMessage");
        var current = status.get();
        if (current == TaskState.WORKING || current == TaskState.INPUT_REQUIRED) {
            this.statusMessage = statusMessage;
            this.lastUpdatedAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    @Override
    public void reportProgress(double progress, @Nullable Double total, @Nullable String message) {}

    public long createdAtMillis() {
        return createdAt;
    }

    public long lastUpdatedAt() {
        return lastUpdatedAt;
    }

    public double ttlSeconds() {
        return ttl != null ? ttl.getSeconds() : 0.0;
    }

    public @Nullable String resultJson() {
        if (result instanceof TaskResult.Completed c) {
            return serializeResult(c.content(), c.structuredContent());
        }
        if (result instanceof TaskResult.Failed f) {
            return serializeResult(f.content(), f.structuredContent());
        }
        return null;
    }

    private static String serializeResult(List<ContentBlock> content, @Nullable Object structured) {
        if (structured != null) {
            return structured.toString();
        }
        if (!content.isEmpty() && content.getFirst() instanceof TextContent tc) {
            return tc.text();
        }
        return "{}";
    }

    /**
     * Transitions to {@code newStatus} without a result value.
     */
    public boolean transitionTo(TaskState newStatus) {
        return transitionTo(newStatus, (TaskResult) null);
    }

    /**
     * Transitions to {@code newStatus}, publishing {@code result} (when non-null).
     */
    public boolean transitionTo(TaskState newStatus, @Nullable TaskResult result) {
        Objects.requireNonNull(newStatus, "status is required");
        if (newStatus == TaskState.COMPLETED) {
            Objects.requireNonNull(result, "result is required when transitioning to completed status");
        }
        var current = status.get();
        if (!current.canTransitionTo(newStatus)) {
            return false;
        }
        if (result != null) {
            this.result = result;
        }
        if (status.compareAndSet(current, newStatus)) {
            this.lastUpdatedAt = System.currentTimeMillis();
            if (newStatus.isTerminal()) {
                if (this.result != null) {
                    completionFuture.complete(this.result);
                } else {
                    completionFuture.completeExceptionally(
                            new IllegalStateException("Terminal state reached without result"));
                }
            }
            return true;
        }
        return false;
    }

    public boolean isExpired() {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        return System.currentTimeMillis() - lastUpdatedAt > ttl.toMillis();
    }

    public String createdAtIso() {
        return Instant.ofEpochMilli(createdAt).toString();
    }

    public String lastUpdatedAtIso() {
        return Instant.ofEpochMilli(lastUpdatedAt).toString();
    }
}
