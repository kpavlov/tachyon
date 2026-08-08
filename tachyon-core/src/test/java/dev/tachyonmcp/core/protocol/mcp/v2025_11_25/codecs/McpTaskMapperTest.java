/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2025_11_25.codecs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.api.server.features.tasks.TaskDescriptor;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.TaskStatus;
import dev.tachyonmcp.core.server.features.tasks.TaskEntry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

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
    void taskResultAndNotificationMappingsPreserveMetadata() {
        var entry = entry(TaskState.WORKING);
        var expected = JsonNodeFactory.instance.stringNode("abc");

        assertThat(McpTaskMapper.toGetTaskResult(entry)._meta()).containsEntry("trace", expected);
        assertThat(McpTaskMapper.toCancelTaskResult(entry)._meta()).containsEntry("trace", expected);
        assertThat(McpTaskMapper.toCreateTaskResult(entry)._meta()).containsEntry("trace", expected);
        assertThat(McpTaskMapper.toStatusNotification(entry)._meta()).containsEntry("trace", expected);
    }

    @Test
    void submittedFoldsToWorkingOnThisVersion() {
        assertThat(McpTaskMapper.toGetTaskResult(entry(TaskState.SUBMITTED)).status())
                .isEqualTo(TaskStatus.WORKING);
    }

    @Test
    void unknownIsNotExpressibleOnThisVersion() {
        assertThatThrownBy(() -> McpTaskMapper.toGetTaskResult(entry(TaskState.UNKNOWN)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ttlIsConvertedToMillisAcrossAllResponseShapes() {
        var withTtl = new TaskEntry(
                TaskDescriptor.builder().id("task-1").build(),
                "task-1",
                TaskState.WORKING,
                Duration.ofSeconds(90),
                null,
                null,
                null);

        assertThat(McpTaskMapper.toTaskProto(withTtl).ttl()).isEqualTo(90_000L);
        assertThat(McpTaskMapper.toGetTaskResult(withTtl).ttl()).isEqualTo(90_000L);
        assertThat(McpTaskMapper.toCancelTaskResult(withTtl).ttl()).isEqualTo(90_000L);
        assertThat(McpTaskMapper.toStatusNotification(withTtl).ttl()).isEqualTo(90_000L);
    }

    @Test
    void nullTtlIsPreservedAcrossAllResponseShapes() {
        var withoutTtl = new TaskEntry(
                TaskDescriptor.builder().id("task-1").build(), "task-1", TaskState.WORKING, null, null, null, null);

        assertThat(McpTaskMapper.toTaskProto(withoutTtl).ttl()).isNull();
        assertThat(McpTaskMapper.toGetTaskResult(withoutTtl).ttl()).isNull();
        assertThat(McpTaskMapper.toCancelTaskResult(withoutTtl).ttl()).isNull();
        assertThat(McpTaskMapper.toStatusNotification(withoutTtl).ttl()).isNull();
    }
}
