/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.server.domain.Task;
import org.jspecify.annotations.Nullable;

public interface TaskRegistry {
    @Nullable
    Task get(String taskId);

    Task create();

    Task create(TaskOptions options);
}
