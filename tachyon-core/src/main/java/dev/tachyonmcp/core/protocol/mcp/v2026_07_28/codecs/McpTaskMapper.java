/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2026_07_28.codecs;

import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.server.features.tasks.TaskEntry;
import dev.tachyonmcp.core.server.json.JsonUtils;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Task wire mapping for MCP 2026-07-28 (SEP-1686). Unlike 2025-11-25, this version exposes the
 * full seven-state workflow: {@code SUBMITTED} maps to {@code "submitted"} (rather than folding to
 * {@code "working"}) and {@code UNKNOWN} maps to {@code "unknown"} (rather than throwing).
 *
 * <p>The 2026-07-28 schema does not generate task wire records, so results are shaped as ordered
 * field maps and rendered to JSON via {@link JsonUtils#toObjectNode(Map)}. The shape mirrors the
 * 2025-11-25 generated codecs exactly: {@code statusMessage}, {@code pollInterval} and
 * {@code _meta} are omitted when absent; {@code ttl} is always present ({@code null} when
 * unlimited).
 */
final class McpTaskMapper {

    private McpTaskMapper() {}

    static String toWireStatus(TaskState status) {
        return switch (status) {
            case SUBMITTED -> "submitted";
            case WORKING -> "working";
            case INPUT_REQUIRED -> "input_required";
            case COMPLETED -> "completed";
            case CANCELLED -> "cancelled";
            case REJECTED, AUTH_REQUIRED, FAILED -> "failed";
            case UNKNOWN -> "unknown";
        };
    }

    static Map<String, Object> toTaskProto(TaskEntry entry) {
        var task = new LinkedHashMap<String, Object>();
        task.put("taskId", entry.id());
        task.put("status", toWireStatus(entry.status()));
        putIfPresent(task, "statusMessage", entry.statusMessage());
        task.put("createdAt", entry.createdAtIso());
        task.put("lastUpdatedAt", entry.lastUpdatedAtIso());
        task.put("ttl", entry.ttl());
        putIfPresent(task, "pollInterval", pollIntervalMillis(entry.pollInterval()));
        return task;
    }

    static JsonNode toGetTaskResult(TaskEntry entry) {
        return JsonUtils.toObjectNode(toTaskResult(entry));
    }

    static JsonNode toCancelTaskResult(TaskEntry entry) {
        return JsonUtils.toObjectNode(toTaskResult(entry));
    }

    static JsonNode toStatusNotification(TaskEntry entry) {
        return JsonUtils.toObjectNode(toTaskResult(entry));
    }

    static JsonNode toCreateTaskResult(TaskEntry entry) {
        var result = new LinkedHashMap<String, Object>();
        result.put("task", toTaskProto(entry));
        putMeta(result, entry);
        return JsonUtils.toObjectNode(result);
    }

    private static Map<String, Object> toTaskResult(TaskEntry entry) {
        var result = new LinkedHashMap<String, Object>();
        putMeta(result, entry);
        result.put("taskId", entry.id());
        result.put("status", toWireStatus(entry.status()));
        putIfPresent(result, "statusMessage", entry.statusMessage());
        result.put("createdAt", entry.createdAtIso());
        result.put("lastUpdatedAt", entry.lastUpdatedAtIso());
        result.put("ttl", entry.ttl());
        putIfPresent(result, "pollInterval", pollIntervalMillis(entry.pollInterval()));
        return result;
    }

    private static void putMeta(Map<String, Object> target, TaskEntry entry) {
        var meta = entry.meta();
        if (meta != null && !meta.isEmpty()) {
            target.put("_meta", meta);
        }
    }

    private static void putIfPresent(Map<String, Object> target, String key, @Nullable Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static @Nullable Long pollIntervalMillis(@Nullable Duration pollInterval) {
        return pollInterval != null ? pollInterval.toMillis() : null;
    }
}
