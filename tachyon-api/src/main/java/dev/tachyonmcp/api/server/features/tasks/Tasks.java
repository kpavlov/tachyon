/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.server.domain.Task;
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
     * Creates a task that can pause for client input, with {@code resumer} receiving each round of
     * answers submitted via {@code tasks/update}.
     *
     * <p>Needed whenever your own code calls {@link Task#requireInput}: a task created without a
     * resumer has nowhere to deliver the answers, so they are dropped and the task simply returns to
     * {@link TaskState#WORKING} with the input lost. Task-augmented tool calls do not need this —
     * the server re-invokes the tool handler for them.
     *
     * @param options task options, as for {@link #create(TaskOptions)}
     * @param resumer invoked once per completed round of input
     * @return the new task, in {@link TaskState#SUBMITTED}
     */
    @ExperimentalApi
    Task create(TaskOptions options, TaskResumer resumer);

    /**
     * Removes a task from the registry, e.g. because it was removed on the caller's side.
     * A non-terminal task is cancelled first (firing a final status notification) so its
     * {@link Task#completion()} doesn't hang forever.
     *
     * @return {@code true} if a task with this id existed and was removed
     */
    boolean remove(String taskId);
}
