/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.domain;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/**
 * Represents an async task that may complete, fail, require input, or report progress.
 *
 * <p>Task-augmented tool calls produce a handle that tracks the operation lifecycle.
 * The caller polls {@code tasks/get} or subscribes to notifications to observe state
 * transitions.
 */
@ExperimentalApi
public interface Task extends HasMeta {

    /** Returns the unique task identifier. */
    String id();

    /** Returns the current task status. */
    TaskState status();

    /** Optional human-readable status message for this task. */
    @Nullable
    String statusMessage();

    /** Timestamp when this task was created. */
    Instant createdAt();

    /** Timestamp when this task's status was last updated. */
    Instant lastUpdatedAt();

    /** Time-to-live duration after which the task is eligible for eviction, or {@code null}. */
    @Nullable
    Duration ttl();

    /** Suggested polling interval for {@code tasks/get}, or {@code null} to not suggest one. */
    @Nullable
    Duration pollInterval();

    /** The task result if the task has reached a terminal state, or {@code null}. */
    @Nullable
    TaskResult result();

    /** A future that completes when the task reaches a terminal state. */
    CompletionStage<TaskResult> completion();

    /**
     * Transitions a freshly created task from {@link TaskState#SUBMITTED} to
     * {@link TaskState#WORKING}. Tasks from {@code Tasks.create()} start out {@code SUBMITTED};
     * call this once the work is actually under way, which also unlocks
     * {@link #updateMessage(String)} and {@link #requireInput(InputRequestBundle, String)}.
     * Task-augmented tool calls are started by the server, so tool handlers never need this.
     *
     * @param statusMessage optional status message
     * @return {@code true} if the task was {@code SUBMITTED} and is now {@code WORKING}
     */
    boolean start(@Nullable String statusMessage);

    /**
     * Transitions the task to the completed state.
     *
     * @param result the completion result
     * @return {@code true} if the state transition was applied
     */
    boolean complete(TaskResult.Completed result);

    /**
     * Transitions the task to the failed state.
     *
     * @param result the failure result
     * @return {@code true} if the state transition was applied
     */
    boolean fail(TaskResult.Failed result);

    /**
     * Requests cancellation of this task.
     *
     * @param statusMessage optional cancellation reason
     * @return {@code true} if the state transition was applied
     */
    boolean cancel(@Nullable String statusMessage);

    /**
     * Signals that this task requires additional input from the client.
     *
     * @param request       the requested inputs and opaque state to echo back
     * @param statusMessage optional status message
     * @return {@code true} if the state transition was applied
     */
    @ExperimentalApi
    boolean requireInput(InputRequestBundle request, @Nullable String statusMessage);

    /**
     * Resumes a task that was waiting for input.
     *
     * @param statusMessage optional status message
     * @return {@code true} if the state transition was applied
     * @deprecated use {@link #start(String)}. This only ever affects a {@link TaskState#SUBMITTED}
     *     task, to which it delegates. An {@link TaskState#INPUT_REQUIRED} task is resumed by the
     *     client answering the pending {@code inputRequests} via {@code tasks/update} — it cannot
     *     be forced from here (doing so would re-invoke the handler with missing answers, or
     *     clobber a fresher pause), so this returns {@code false} in that state.
     */
    @Deprecated(forRemoval = false)
    default boolean resume(@Nullable String statusMessage) {
        return start(statusMessage);
    }

    /**
     * Updates the status message of this task.
     *
     * @param statusMessage the new status message
     * @return {@code true} if the message was updated
     */
    boolean updateMessage(String statusMessage);

    /**
     * Reports progress for this task.
     *
     * @param progress the current progress value
     * @param total    the total expected value, or {@code null} if unknown
     * @param message  optional progress message
     */
    void reportProgress(double progress, @Nullable Double total, @Nullable String message);
}
