/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2026_07_28.codecs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.features.tasks.TaskDescriptor;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.server.features.tasks.TaskEntry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 2026-07-28 task status mapping (SEP-1686). Confirms the two states 2025-11-25 cannot express on
 * the wire: {@code SUBMITTED → "submitted"} (2025 folds to {@code "working"}) and
 * {@code UNKNOWN → "unknown"} (2025 throws).
 */
class McpTaskMapperTest {

    private static TaskEntry entry(TaskState status) {
        return new TaskEntry(
                TaskDescriptor.builder().id("task-1").build(),
                "task-1",
                status,
                Duration.ofMinutes(1),
                "session-1",
                null,
                Map.of("trace", "abc"));
    }

    @Test
    void submittedMapsToSubmittedWireString() {
        var node = McpTaskMapper.toGetTaskResult(entry(TaskState.SUBMITTED));
        assertThat(node.get("status").asString()).isEqualTo("submitted");
        assertThat(node.get("taskId").asString()).isEqualTo("task-1");
        assertThat(node.get("createdAt").asString()).isNotEmpty();
        assertThat(node.get("lastUpdatedAt").asString()).isNotEmpty();
    }

    @Test
    void unknownMapsToUnknownWireStringInsteadOfThrowing() {
        var node = McpTaskMapper.toGetTaskResult(entry(TaskState.UNKNOWN));
        assertThat(node.get("status").asString()).isEqualTo("unknown");
    }

    @Test
    void sharedStatesMatchTheFiveClassicWireStrings() {
        assertThat(McpTaskMapper.toWireStatus(TaskState.WORKING)).isEqualTo("working");
        assertThat(McpTaskMapper.toWireStatus(TaskState.INPUT_REQUIRED)).isEqualTo("input_required");
        assertThat(McpTaskMapper.toWireStatus(TaskState.COMPLETED)).isEqualTo("completed");
        assertThat(McpTaskMapper.toWireStatus(TaskState.CANCELLED)).isEqualTo("cancelled");
        assertThat(McpTaskMapper.toWireStatus(TaskState.FAILED)).isEqualTo("failed");
        assertThat(McpTaskMapper.toWireStatus(TaskState.REJECTED)).isEqualTo("failed");
        assertThat(McpTaskMapper.toWireStatus(TaskState.AUTH_REQUIRED)).isEqualTo("failed");
    }

    @Test
    void wireShapeOmitsNullFieldsButAlwaysWritesTtlAndMeta() {
        var node = McpTaskMapper.toGetTaskResult(entry(TaskState.SUBMITTED));
        assertThat(node.has("ttl")).isTrue();
        assertThat(node.get("ttl").asLong()).isEqualTo(Duration.ofMinutes(1).toMillis());
        assertThat(node.has("statusMessage")).isFalse();
        assertThat(node.has("pollInterval")).isFalse();
        assertThat(node.get("_meta").get("trace").asString()).isEqualTo("abc");
    }

    @Test
    void createTaskResultNestsTaskUnderTaskKey() {
        var node = McpTaskMapper.toCreateTaskResult(entry(TaskState.SUBMITTED));
        assertThat(node.get("task").get("status").asString()).isEqualTo("submitted");
        assertThat(node.get("task").get("taskId").asString()).isEqualTo("task-1");
        assertThat(node.get("_meta").get("trace").asString()).isEqualTo("abc");
    }

    @Test
    void cancelAndNotificationCarryTheSameStatus() {
        assertThat(McpTaskMapper.toCancelTaskResult(entry(TaskState.CANCELLED))
                        .get("status")
                        .asString())
                .isEqualTo("cancelled");
        assertThat(McpTaskMapper.toStatusNotification(entry(TaskState.INPUT_REQUIRED))
                        .get("status")
                        .asString())
                .isEqualTo("input_required");
    }
}
