/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CancelTaskResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CreateTaskResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.GetTaskResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Task;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatusNotificationParams;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tasks.TaskState;
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
                entry.ttl(),
                pollIntervalToMillis(entry.pollInterval()));
    }

    static GetTaskResult toGetTaskResult(TaskEntry entry) {
        return new GetTaskResult(
                null,
                entry.id(),
                toWireStatus(entry.status()),
                entry.statusMessage(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttl(),
                pollIntervalToMillis(entry.pollInterval()));
    }

    public static CancelTaskResult toCancelTaskResult(TaskEntry entry) {
        return new CancelTaskResult(
                null,
                entry.id(),
                toWireStatus(entry.status()),
                entry.statusMessage(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttl(),
                pollIntervalToMillis(entry.pollInterval()));
    }

    static CreateTaskResult toCreateTaskResult(TaskEntry entry) {
        return new CreateTaskResult(toTaskProto(entry), null, null);
    }

    static TaskStatusNotificationParams toStatusNotification(TaskEntry entry) {
        return new TaskStatusNotificationParams(
                null,
                entry.id(),
                toWireStatus(entry.status()),
                entry.statusMessage(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttl(),
                pollIntervalToMillis(entry.pollInterval()));
    }
}
