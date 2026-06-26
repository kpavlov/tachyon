/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.*;
import dev.tachyonmcp.server.features.tasks.TaskEntry;
import dev.tachyonmcp.server.features.tasks.TaskState;

final class McpTaskMapper {

    private McpTaskMapper() {}

    private static TaskStatus toWireStatus(TaskState status) {
        return switch (status) {
            case SUBMITTED, WORKING -> TaskStatus.WORKING;
            case REJECTED, AUTH_REQUIRED, FAILED -> TaskStatus.FAILED;
            case INPUT_REQUIRED -> TaskStatus.INPUT_REQUIRED;
            case COMPLETED -> TaskStatus.COMPLETED;
            case CANCELLED -> TaskStatus.CANCELLED;
            case TASK_STATE_UNSPECIFIED -> throw new UnsupportedOperationException("Unsupported status: " + status);
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

    static Task toTaskProto(TaskEntry entry) {
        return new Task(
                entry.id(),
                toWireStatus(entry.status()),
                entry.status().toString(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttl(),
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
                entry.ttl(),
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
                entry.ttl(),
                null);
    }

    static TaskStatusNotificationParams toStatusNotification(TaskEntry entry) {
        return new TaskStatusNotificationParams(
                null,
                entry.id(),
                toWireStatus(entry.status()),
                entry.status().toString(),
                entry.createdAtIso(),
                entry.lastUpdatedAtIso(),
                entry.ttl(),
                null);
    }
}
