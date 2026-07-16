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

    static TaskState toInternalStatus(TaskStatus wireStatus) {
        return switch (wireStatus) {
            case WORKING -> TaskState.WORKING;
            case INPUT_REQUIRED -> TaskState.INPUT_REQUIRED;
            case COMPLETED -> TaskState.COMPLETED;
            case FAILED -> TaskState.FAILED;
            case CANCELLED -> TaskState.CANCELLED;
        };
    }

    private static double ttlToMillis(@Nullable Duration ttl) {
        return ttl != null ? (double) ttl.toMillis() : 0.0;
    }

    static Task toTaskProto(TaskEntry entry) {
        return new Task(
                entry.id(),
                toWireStatus(entry.status()),
                entry.status().toString(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                ttlToMillis(entry.ttl()),
                null);
    }

    static GetTaskResult toGetTaskResult(TaskEntry entry) {
        return new GetTaskResult(
                null,
                entry.id(),
                toWireStatus(entry.status()),
                entry.status().toString(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                ttlToMillis(entry.ttl()),
                null);
    }

    public static CancelTaskResult toCancelTaskResult(TaskEntry entry) {
        return new CancelTaskResult(
                null,
                entry.id(),
                toWireStatus(entry.status()),
                entry.status().toString(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                ttlToMillis(entry.ttl()),
                null);
    }

    static CreateTaskResult toCreateTaskResult(TaskEntry entry) {
        return new CreateTaskResult(toTaskProto(entry), null, null);
    }

    static TaskStatusNotificationParams toStatusNotification(TaskEntry entry) {
        final var ttl = entry.ttl();
        return new TaskStatusNotificationParams(
                null,
                entry.id(),
                toWireStatus(entry.status()),
                entry.status().toString(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                ttl != null ? ttl.toMillis() : null,
                null);
    }
}
