/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import dev.tachyonmcp.annotations.ExperimentalApi;
import dev.tachyonmcp.server.features.tasks.TaskState;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

@ExperimentalApi
public interface Task extends HasMeta {

    String id();

    TaskState status();

    @Nullable
    String statusMessage();

    Instant createdAt();

    @Nullable
    Duration ttl();

    /** Suggested polling interval for {@code tasks/get}, or {@code null} to not suggest one. */
    @Nullable
    Duration pollInterval();

    @Nullable
    TaskResult result();

    CompletionStage<TaskResult> completion();

    boolean complete(TaskResult.Completed result);

    boolean fail(TaskResult.Failed result);

    boolean cancel(@Nullable String statusMessage);

    boolean requireInput(InputRequest request, @Nullable String statusMessage);

    boolean resume(@Nullable String statusMessage);

    boolean updateMessage(String statusMessage);

    void reportProgress(double progress, @Nullable Double total, @Nullable String message);
}
