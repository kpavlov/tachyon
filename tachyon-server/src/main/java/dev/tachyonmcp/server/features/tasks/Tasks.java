/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.server.domain.Task;
import org.jspecify.annotations.Nullable;

/**
 * Façade interface for MCP tasks
 */
public interface Tasks {
    @Nullable
    Task get(String taskId);

    Task create();

    Task create(TaskOptions options);

    /**
     * Removes a task from the registry, e.g. because it was removed on the caller's side.
     * A non-terminal task is cancelled first (firing a final status notification) so its
     * {@link Task#completion()} doesn't hang forever.
     *
     * @return {@code true} if a task with this id existed and was removed
     */
    boolean remove(String taskId);
}
