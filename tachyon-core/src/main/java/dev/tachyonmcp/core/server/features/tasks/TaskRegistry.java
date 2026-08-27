/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.domain.ProgressToken;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import dev.tachyonmcp.api.server.features.tasks.Tasks;
import org.jspecify.annotations.Nullable;

/** Internal server task projection cache. */
@InternalApi
public interface TaskRegistry extends Tasks {

    /** Returns whether an external task execution connector is configured. */
    boolean executionConfigured();

    /**
     * Publishes the initial projection for a task-augmented tool call, capturing
     * {@code progressToken} for later {@link Tasks#reportProgress}. Only meaningful on task
     * creation — ignored when the task already exists.
     */
    TaskSnapshot publish(TaskSnapshot snapshot, @Nullable ProgressToken progressToken);
}
