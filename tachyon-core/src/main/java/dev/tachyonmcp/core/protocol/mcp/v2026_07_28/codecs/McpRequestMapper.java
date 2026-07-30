/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2026_07_28.codecs;

import dev.tachyonmcp.api.server.features.tasks.TaskState;
import org.jspecify.annotations.Nullable;

/**
 * Request mapper for MCP 2026-07-28.
 *
 * <p>Task status uses the wider 2026-07-28 workflow (SEP-1686): it adds the {@code submitted}
 * (initial pre-{@code working}) and {@code unknown} (terminal fallback) states on top of the five
 * 2025-11-25 states. {@link #taskStatus(Object)} is overridden to parse those two extra strings;
 * everything else is inherited from the 2025-11-25 mapper unchanged.
 */
public final class McpRequestMapper extends dev.tachyonmcp.core.protocol.mcp.v2025_11_25.codecs.McpRequestMapper {

    @Override
    public @Nullable TaskStatusRequest taskStatus(@Nullable Object params) {
        var map = asMap(params);
        if (!(map.get("taskId") instanceof String taskId)) return null;
        var state = toTaskState(map.get("status"));
        var message = map.get("statusMessage") instanceof String text ? text : null;
        return new TaskStatusRequest(taskId, state, message);
    }

    private static TaskState toTaskState(@Nullable Object status) {
        if (!(status instanceof String text)) return TaskState.WORKING;
        return switch (text) {
            case "submitted" -> TaskState.SUBMITTED;
            case "input_required" -> TaskState.INPUT_REQUIRED;
            case "completed" -> TaskState.COMPLETED;
            case "failed" -> TaskState.FAILED;
            case "cancelled" -> TaskState.CANCELLED;
            case "unknown" -> TaskState.UNKNOWN;
            default -> TaskState.WORKING;
        };
    }
}
