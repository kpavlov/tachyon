/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CancelTaskResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.GetTaskResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Task;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatusNotificationParams;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tasks.TaskState;
import org.jspecify.annotations.Nullable;

public final class McpTaskMapper {

    private McpTaskMapper() {}

    @Nullable
    public static TaskStatus toWireStatus(TaskState status) {
        return switch (status) {
            case SUBMITTED -> null;
            case REJECTED -> null;
            case AUTH_REQUIRED -> null;
            case WORKING -> TaskStatus.WORKING;
            case INPUT_REQUIRED -> TaskStatus.INPUT_REQUIRED;
            case COMPLETED -> TaskStatus.COMPLETED;
            case FAILED -> TaskStatus.FAILED;
            case CANCELLED -> TaskStatus.CANCELLED;
            case TASK_STATE_UNSPECIFIED -> null;
        };
    }

    public static TaskState toInternalStatus(TaskStatus wireStatus) {
        return switch (wireStatus) {
            case WORKING -> TaskState.WORKING;
            case INPUT_REQUIRED -> TaskState.INPUT_REQUIRED;
            case COMPLETED -> TaskState.COMPLETED;
            case FAILED -> TaskState.FAILED;
            case CANCELLED -> TaskState.CANCELLED;
        };
    }

    public static Task toTaskProto(TaskEntry entry) {
        return new Task(
                entry.id(),
                toWireStatus(entry.status()),
                null,
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttl(),
                null);
    }

    public static GetTaskResult toGetTaskResult(TaskEntry entry) {
        return new GetTaskResult(
                null,
                entry.id(),
                toWireStatus(entry.status()),
                null,
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttl(),
                null);
    }

    public static CancelTaskResult toCancelTaskResult(TaskEntry entry) {
        return new CancelTaskResult(
                null,
                entry.id(),
                toWireStatus(entry.status()),
                null,
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttl(),
                null);
    }

    public static TaskStatusNotificationParams toStatusNotification(TaskEntry entry) {
        return new TaskStatusNotificationParams(
                null,
                entry.id(),
                toWireStatus(entry.status()),
                null,
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttl(),
                null);
    }
}
