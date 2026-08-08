/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2026_07_28.codecs;

import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.server.features.tasks.TaskEntry;
import dev.tachyonmcp.core.server.json.JsonUtils;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Task wire mapping for MCP 2026-07-28's tasks extension (SEP-2663). Field names and shapes
 * follow the extension spec rather than 2025-11-25's experimental tasks feature: {@code ttlMs}/
 * {@code pollIntervalMs} (not {@code ttl}/{@code pollInterval}), a {@code resultType}
 * discriminator ({@code "task"} on task creation, {@code "complete"} on {@code tasks/get}), a
 * flat {@code CreateTaskResult} shape (@code CreateTaskResult = Result & Task} — no nesting under
 * a {@code "task"} key), and {@code tasks/cancel} returning an empty acknowledgement rather than
 * the full task state.
 *
 * <p>{@code SUBMITTED} maps to {@code "submitted"} and {@code UNKNOWN} to {@code "unknown"} — two
 * states 2025-11-25 cannot express on the wire (it folds {@code SUBMITTED} to {@code "working"}
 * and throws on {@code UNKNOWN}).
 */
final class McpTaskMapper {

    private static final String RESULT_TYPE_TASK = "task";
    private static final String RESULT_TYPE_COMPLETE = "complete";

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

    static JsonNode toCreateTaskResult(TaskEntry entry) {
        var fields = taskFields(entry, toWireStatus(entry.status()));
        fields.put("resultType", RESULT_TYPE_TASK);
        return JsonUtils.toObjectNode(fields);
    }

    static JsonNode toGetTaskResult(TaskEntry entry, @Nullable JsonNode inlineResult, @Nullable JsonNode inlineError) {
        var fields = taskFields(entry, effectiveWireStatus(entry));
        putIfPresent(fields, "result", inlineResult);
        putIfPresent(fields, "error", inlineError);
        fields.put("resultType", RESULT_TYPE_COMPLETE);
        return JsonUtils.toObjectNode(fields);
    }

    /** {@code tasks/cancel}'s response is an empty acknowledgement, not the task's state. */
    static JsonNode toCancelTaskResult() {
        return JsonUtils.toObjectNode(Map.of("resultType", RESULT_TYPE_COMPLETE));
    }

    static JsonNode toStatusNotification(TaskEntry entry) {
        return JsonUtils.toObjectNode(taskFields(entry, effectiveWireStatus(entry)));
    }

    /**
     * A tool result that completed with {@code isError: true} is a normal {@code completed}
     * outcome, not a task failure: "This status MUST NOT be used for non-JSON-RPC errors ...
     * errors within the context of a protocol method result MUST use the completed status." Only
     * a {@link TaskResult.Failed} carrying a {@code protocolError} is a genuine JSON-RPC failure.
     */
    private static String effectiveWireStatus(TaskEntry entry) {
        if (entry.result() instanceof TaskResult.Failed failed && failed.protocolError() == null) {
            return "completed";
        }
        return toWireStatus(entry.status());
    }

    private static Map<String, Object> taskFields(TaskEntry entry, String wireStatus) {
        var fields = new LinkedHashMap<String, Object>();
        putMeta(fields, entry);
        fields.put("taskId", entry.id());
        fields.put("status", wireStatus);
        putIfPresent(fields, "statusMessage", entry.statusMessage());
        fields.put("createdAt", entry.createdAt());
        fields.put("lastUpdatedAt", entry.lastUpdatedAt());
        // SEP-2663 types Task.ttlMs as `number | null` (required, nullable) but
        // Task.pollIntervalMs as `number?` (optional) -- ttlMs is always written, even when null;
        // pollIntervalMs is omitted rather than written as null.
        fields.put("ttlMs", entry.ttlMillis());
        putIfPresent(fields, "pollIntervalMs", pollIntervalMillis(entry.pollInterval()));
        return fields;
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
