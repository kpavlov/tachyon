/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2026_07_28.codecs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.features.tasks.TaskState;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 2026-07-28 inbound task-status parsing (SEP-1686). The two extra states — {@code "submitted"} and
 * {@code "unknown"} — must parse to their own {@link TaskState}; on 2025-11-25 both fall through to
 * {@code WORKING}.
 */
class McpRequestMapperTest {

    private final McpRequestMapper mapper = new McpRequestMapper();

    private TaskState parse(String wireStatus) {
        var request = mapper.taskStatus(Map.of("taskId", "t1", "status", wireStatus));
        assertThat(request).isNotNull();
        assertThat(request.taskId()).isEqualTo("t1");
        return request.state();
    }

    @Test
    void parsesSubmittedAndUnknown() {
        assertThat(parse("submitted")).isEqualTo(TaskState.SUBMITTED);
        assertThat(parse("unknown")).isEqualTo(TaskState.UNKNOWN);
    }

    @Test
    void parsesTheFiveClassicStates() {
        assertThat(parse("input_required")).isEqualTo(TaskState.INPUT_REQUIRED);
        assertThat(parse("completed")).isEqualTo(TaskState.COMPLETED);
        assertThat(parse("failed")).isEqualTo(TaskState.FAILED);
        assertThat(parse("cancelled")).isEqualTo(TaskState.CANCELLED);
        assertThat(parse("working")).isEqualTo(TaskState.WORKING);
    }

    @Test
    void unrecognisedStatusFallsBackToWorking() {
        assertThat(parse("nonsense")).isEqualTo(TaskState.WORKING);
    }

    @Test
    void carriesStatusMessageAndReturnsNullWithoutTaskId() {
        var request = mapper.taskStatus(Map.of("taskId", "t1", "status", "unknown", "statusMessage", "boom"));
        assertThat(request).isNotNull();
        assertThat(request.message()).isEqualTo("boom");
        assertThat(mapper.taskStatus(Map.of("status", "submitted"))).isNull();
    }
}
