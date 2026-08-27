/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.JsonObject;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskBuildersTest {

    @Test
    void buildsExecutionRequestWithoutPublicImplementationType() {
        var request = TaskExecutionRequest.builder()
                .taskId("task-1")
                .operation("book_appointment")
                .arguments(JsonObject.of(Map.of("city", "Tallinn")))
                .meta(Map.of("tenant", "example"))
                .build();

        assertThat(TaskExecutionRequest.class).isInterface();
        assertThat(request.taskId()).isEqualTo("task-1");
        assertThat(request.operation()).isEqualTo("book_appointment");
        assertThat(request.arguments().stringValue("city")).isEqualTo("Tallinn");
        assertThat(request.meta()).containsEntry("tenant", "example");
    }

    @Test
    void buildsAndCopiesTaskSnapshot() {
        var observedAt = Instant.parse("2026-08-27T07:00:00Z");
        var working = TaskSnapshot.working("task-1", observedAt, 1);

        var completed = TaskSnapshot.builder()
                .from(working)
                .status(TaskState.COMPLETED)
                .statusMessage("Done")
                .revision(2)
                .build();

        assertThat(TaskSnapshot.class).isInterface();
        assertThat(completed.taskId()).isEqualTo("task-1");
        assertThat(completed.status()).isEqualTo(TaskState.COMPLETED);
        assertThat(completed.createdAt()).isEqualTo(observedAt);
        assertThat(completed.revision()).isEqualTo(2);
    }

    @Test
    void buildsAndCopiesTaskInput() {
        var input = TaskInput.builder()
                .inputResponses(Map.of("approved", true))
                .requestState("round-1")
                .build();

        var copy = TaskInput.builder().from(input).build();

        assertThat(TaskInput.class).isInterface();
        assertThat(copy.inputResponses()).containsEntry("approved", true);
        assertThat(copy.requestState()).isEqualTo("round-1");
    }
}
