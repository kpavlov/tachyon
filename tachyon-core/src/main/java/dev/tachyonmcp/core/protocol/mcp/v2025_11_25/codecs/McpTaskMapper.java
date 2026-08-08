/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.CancelTaskResult;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.CreateTaskResult;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.GetTaskResult;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.Task;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.TaskStatus;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.TaskStatusNotificationParams;
import dev.tachyonmcp.core.server.features.tasks.TaskEntry;
import dev.tachyonmcp.core.server.json.JsonUtils;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

final class McpTaskMapper {

    private McpTaskMapper() {}

    private static TaskStatus toWireStatus(TaskState status) {
        return switch (status) {
            case SUBMITTED, WORKING -> TaskStatus.WORKING;
            case REJECTED, AUTH_REQUIRED, FAILED -> TaskStatus.FAILED;
            case INPUT_REQUIRED -> TaskStatus.INPUT_REQUIRED;
            case COMPLETED -> TaskStatus.COMPLETED;
            case CANCELLED -> TaskStatus.CANCELLED;
            case UNKNOWN -> throw new UnsupportedOperationException("Unsupported status: " + status);
        };
    }

    private static @Nullable Long pollIntervalToMillis(@Nullable Duration pollInterval) {
        return pollInterval != null ? pollInterval.toMillis() : null;
    }

    static Task toTaskProto(TaskEntry entry) {
        return new Task(
                entry.id(),
                toWireStatus(entry.status()),
                entry.statusMessage(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttlMillis(),
                pollIntervalToMillis(entry.pollInterval()));
    }

    static GetTaskResult toGetTaskResult(TaskEntry entry) {
        return new GetTaskResult(
                JsonUtils.toJsonNodeMap(entry.meta()),
                entry.id(),
                toWireStatus(entry.status()),
                entry.statusMessage(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttlMillis(),
                pollIntervalToMillis(entry.pollInterval()));
    }

    public static CancelTaskResult toCancelTaskResult(TaskEntry entry) {
        return new CancelTaskResult(
                JsonUtils.toJsonNodeMap(entry.meta()),
                entry.id(),
                toWireStatus(entry.status()),
                entry.statusMessage(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttlMillis(),
                pollIntervalToMillis(entry.pollInterval()));
    }

    static CreateTaskResult toCreateTaskResult(TaskEntry entry) {
        return new CreateTaskResult(toTaskProto(entry), JsonUtils.toJsonNodeMap(entry.meta()), null);
    }

    static TaskStatusNotificationParams toStatusNotification(TaskEntry entry) {
        return new TaskStatusNotificationParams(
                JsonUtils.toJsonNodeMap(entry.meta()),
                entry.id(),
                toWireStatus(entry.status()),
                entry.statusMessage(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttlMillis(),
                pollIntervalToMillis(entry.pollInterval()));
    }
}
