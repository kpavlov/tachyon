/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.server.domain.Task;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Receives the input a client submitted for a task that was waiting on it.
 *
 * <p>Supply one to {@link Tasks#create(TaskOptions, TaskResumer)} when your own code creates a task
 * and pauses it with {@link Task#requireInput}. Without a resumer the submitted answers have nowhere
 * to go and are discarded — only task-augmented tool calls get one wired automatically, because
 * there the server can simply re-invoke the tool handler.
 *
 * <p>Called once per round, after every outstanding key of the pending
 * {@link dev.tachyonmcp.api.server.domain.InputRequestBundle} has an answer; the task has already
 * moved back to {@link TaskState#WORKING} by then. Drive the task onwards from here: complete it,
 * fail it, or park it again with another {@link Task#requireInput}. Throwing fails the task.
 */
@ExperimentalApi
@FunctionalInterface
public interface TaskResumer {

    /**
     * Handles one round of submitted input.
     *
     * @param task           the task that was waiting, already back in {@link TaskState#WORKING}
     * @param inputResponses the answers, keyed as in the pending request bundle — unknown keys are
     *                       already filtered out
     * @param requestState   the opaque state passed to {@link Task#requireInput}, echoed back
     */
    void resume(Task task, Map<String, Object> inputResponses, @Nullable String requestState);
}
