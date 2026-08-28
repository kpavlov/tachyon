/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import org.jspecify.annotations.Nullable;

/**
 * Façade interface for MCP tasks
 */
@ExperimentalApi
public interface Tasks {
    /** Publishes a complete task projection and returns the effective cached snapshot. */
    TaskSnapshot publish(TaskSnapshot snapshot);

    /** Returns the cached task projection, or {@code null} when absent. */
    @Nullable
    TaskSnapshot get(String taskId);

    /**
     * Removes a cached task projection without changing externally owned work.
     *
     * @return {@code true} if a task with this id existed and was removed
     */
    boolean remove(String taskId);

    /**
     * Reports progress for a task-augmented tool call, emitted as {@code notifications/progress}
     * to the progress token of the request that created the task.
     *
     * <p>A no-op, logged at debug, when {@code taskId} is unknown or the task was created without
     * a progress token — e.g. every task created via {@link #publish} directly, since {@link
     * TaskSnapshot} carries no token; only the initial snapshot returned by a task-augmented tool
     * handler captures one.
     *
     * @param taskId   the task to report progress for
     * @param progress the current progress value
     * @param total    the total expected value, or {@code null} if unknown
     * @param message  optional progress message
     */
    void reportProgress(String taskId, double progress, @Nullable Double total, @Nullable String message);
}
