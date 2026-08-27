/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2026_07_28.codecs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * MCP 2026-07-28 task wire mapping (SEP-2663): {@code ttlMs}/{@code pollIntervalMs} field names,
 * the {@code resultType} discriminator, a flat {@code CreateTaskResult}, and an empty-ack
 * {@code CancelTaskResult} — none of which match 2025-11-25's experimental tasks shape.
 */
class McpTaskMapperTest {

    private static TaskSnapshot entry(TaskState status) {
        return TaskSnapshot.builder()
                .taskId("task-1")
                .status(status)
                .ttl(Duration.ofMinutes(1))
                .meta(Map.of("trace", "abc"))
                .createdAt(Instant.EPOCH)
                .lastUpdatedAt(Instant.EPOCH)
                .revision(1)
                .build();
    }

    @Test
    void submittedMapsToSubmittedWireString() {
        var node = McpTaskMapper.toGetTaskResult(entry(TaskState.SUBMITTED), null, null, null);
        assertThat(node.get("status").asString()).isEqualTo("submitted");
        assertThat(node.get("taskId").asString()).isEqualTo("task-1");
        assertThat(node.get("createdAt").asString()).isNotEmpty();
        assertThat(node.get("lastUpdatedAt").asString()).isNotEmpty();
        assertThat(node.get("resultType").asString()).isEqualTo("complete");
    }

    @Test
    void unknownMapsToUnknownWireStringInsteadOfThrowing() {
        var node = McpTaskMapper.toGetTaskResult(entry(TaskState.UNKNOWN), null, null, null);
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
    void wireShapeOmitsNullFieldsButAlwaysWritesTtlMsAndMeta() {
        var node = McpTaskMapper.toGetTaskResult(entry(TaskState.SUBMITTED), null, null, null);
        assertThat(node.has("ttlMs")).isTrue();
        assertThat(node.get("ttlMs").asLong()).isEqualTo(Duration.ofMinutes(1).toMillis());
        assertThat(node.has("statusMessage")).isFalse();
        assertThat(node.has("pollIntervalMs")).isFalse();
        assertThat(node.get("_meta").get("trace").asString()).isEqualTo("abc");
    }

    @Test
    void ttlMsIsWrittenAsNullRatherThanOmittedWhenUnlimited() {
        // SEP-2663 types Task.ttlMs as `number | null` (required) unlike the optional
        // pollIntervalMs -- an unlimited task must still report the key, just with a null value.
        var unlimited = TaskSnapshot.builder()
                .taskId("task-2")
                .status(TaskState.SUBMITTED)
                .createdAt(Instant.EPOCH)
                .lastUpdatedAt(Instant.EPOCH)
                .revision(1)
                .build();

        var node = McpTaskMapper.toGetTaskResult(unlimited, null, null, null);

        assertThat(node.has("ttlMs")).isTrue();
        assertThat(node.get("ttlMs").isNull()).isTrue();
    }

    @Test
    void getTaskResultInlinesTheGivenResultNode() {
        var resultNode = JsonNodeFactory.instance.objectNode().put("text", "done");

        var node = McpTaskMapper.toGetTaskResult(entry(TaskState.COMPLETED), resultNode, null, null);

        assertThat(node.get("result").get("text").asString()).isEqualTo("done");
        assertThat(node.has("error")).isFalse();
    }

    @Test
    void getTaskResultInlinesTheGivenErrorNode() {
        var errorNode = JsonNodeFactory.instance.objectNode().put("code", -32603);

        var node = McpTaskMapper.toGetTaskResult(entry(TaskState.FAILED), null, errorNode, null);

        assertThat(node.get("error").get("code").asInt()).isEqualTo(-32603);
        assertThat(node.has("result")).isFalse();
    }

    @Test
    void getTaskResultInlinesTheGivenInputRequestsNode() {
        var inputRequestsNode = JsonNodeFactory.instance.objectNode().put("field", "value");

        var node = McpTaskMapper.toGetTaskResult(entry(TaskState.INPUT_REQUIRED), null, null, inputRequestsNode);

        assertThat(node.get("inputRequests")).isEqualTo(inputRequestsNode);
        assertThat(node.has("result")).isFalse();
        assertThat(node.has("error")).isFalse();
    }

    @Test
    void toolLevelErrorReportsCompletedStatusForGetAndCreate() {
        var task = TaskSnapshot.builder()
                .from(entry(TaskState.WORKING))
                .status(TaskState.FAILED)
                .result(TaskResult.failed("boom"))
                .revision(2)
                .build();

        var getNode = McpTaskMapper.toGetTaskResult(task, null, null, null);
        var createNode = McpTaskMapper.toCreateTaskResult(task);

        assertThat(getNode.get("status").asString()).isEqualTo("completed");
        assertThat(createNode.get("status").asString()).isEqualTo("completed");
        assertThat(createNode.get("resultType").asString()).isEqualTo("task");
    }

    @Test
    void protocolErrorReportsFailedStatus() {
        var task = TaskSnapshot.builder()
                .from(entry(TaskState.WORKING))
                .status(TaskState.FAILED)
                .result(TaskResult.failed(new ServerError(ServerError.Kind.INTERNAL_ERROR, "boom")))
                .revision(2)
                .build();

        var node = McpTaskMapper.toGetTaskResult(task, null, null, null);

        assertThat(node.get("status").asString()).isEqualTo("failed");
    }

    @Test
    void createTaskResultIsFlatWithTaskDiscriminator() {
        var node = McpTaskMapper.toCreateTaskResult(entry(TaskState.SUBMITTED));
        assertThat(node.get("status").asString()).isEqualTo("submitted");
        assertThat(node.get("taskId").asString()).isEqualTo("task-1");
        assertThat(node.get("resultType").asString()).isEqualTo("task");
        assertThat(node.get("_meta").get("trace").asString()).isEqualTo("abc");
        assertThat(node.has("task")).isFalse();
    }

    @Test
    void cancelTaskResultIsAnEmptyAcknowledgement() {
        var node = McpTaskMapper.toCancelTaskResult();
        assertThat(node.get("resultType").asString()).isEqualTo("complete");
        assertThat(node.has("taskId")).isFalse();
        assertThat(node.has("status")).isFalse();
    }

    @Test
    void statusNotificationCarriesTheCurrentStatus() {
        assertThat(McpTaskMapper.toStatusNotification(entry(TaskState.INPUT_REQUIRED))
                        .get("status")
                        .asString())
                .isEqualTo("input_required");
    }
}
