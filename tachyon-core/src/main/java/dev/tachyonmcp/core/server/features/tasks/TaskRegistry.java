/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.domain.ProgressToken;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.api.server.features.tasks.Tasks;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;

@InternalApi
public interface TaskRegistry extends Tasks {

    TaskEntry createSessionTask(
            @Nullable Duration ttl,
            @Nullable Map<String, Object> meta,
            @Nullable String sessionId,
            @Nullable ProgressToken progressToken);

    void registerRunning(String taskId, Future<?> future);

    void unregisterRunning(String taskId);

    void registerResumer(String taskId, TaskResumer resumer);

    void unregisterResumer(String taskId);

    @Nullable
    TaskResumer findResumer(String taskId);

    void add(TaskEntry entry);

    @Nullable
    TaskEntry getById(String taskId);

    boolean updateStatus(String taskId, TaskState newStatus, @Nullable String statusMessage);
}
