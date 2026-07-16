/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.annotations.InternalApi;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@InternalApi
public interface InternalTaskRegistry extends TaskRegistry {

    TaskEntry createSessionTask(
            @Nullable Duration ttl,
            @Nullable Map<String, JsonNode> meta,
            @Nullable String sessionId,
            @Nullable Object progressToken);

    void registerRunning(String taskId, Future<?> future);

    void unregisterRunning(String taskId);

    void add(TaskEntry entry);

    @Nullable
    TaskEntry getById(String taskId);

    boolean updateStatus(String taskId, TaskState newStatus, @Nullable String statusMessage);
}
