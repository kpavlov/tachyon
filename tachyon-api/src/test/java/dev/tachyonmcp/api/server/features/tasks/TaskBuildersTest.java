/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.api.server.domain.TaskResult;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskBuildersTest {

    @Test
    void buildsAndCopiesTaskSnapshot() {
        var observedAt = Instant.parse("2026-08-27T07:00:00Z");
        var working = TaskSnapshot.working("task-1", observedAt, 1);

        var completed = TaskSnapshot.builder()
                .from(working)
                .status(TaskState.COMPLETED)
                .statusMessage("Done")
                .result(TaskResult.completed(Map.of("bookingId", "booking-1")))
                .revision(2)
                .build();

        assertThat(TaskSnapshot.class).isInterface();
        assertThat(completed.taskId()).isEqualTo("task-1");
        assertThat(completed.status()).isEqualTo(TaskState.COMPLETED);
        assertThat(completed.createdAt()).isEqualTo(observedAt);
        assertThat(completed.revision()).isEqualTo(2);
    }

    @Test
    void completedSnapshotRequiresACompletedResult() {
        var working = TaskSnapshot.working("task-2", Instant.EPOCH, 1);

        assertThatThrownBy(() -> TaskSnapshot.builder()
                        .from(working)
                        .status(TaskState.COMPLETED)
                        .revision(2)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void nonTerminalSnapshotCannotCarryAResult() {
        var working = TaskSnapshot.working("task-3", Instant.EPOCH, 1);

        assertThatThrownBy(() -> TaskSnapshot.builder()
                        .from(working)
                        .result(TaskResult.completed(Map.of()))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WORKING");
    }

    @Test
    void inputRequiredSnapshotRequiresPendingInput() {
        var working = TaskSnapshot.working("task-4", Instant.EPOCH, 1);

        assertThatThrownBy(() -> TaskSnapshot.builder()
                        .from(working)
                        .status(TaskState.INPUT_REQUIRED)
                        .revision(2)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INPUT_REQUIRED");
    }

    @Test
    void buildsAndCopiesTaskUpdateRequest() {
        var request = TaskUpdateRequest.builder()
                .taskId("task-1")
                .inputResponses(Map.of("approved", true))
                .meta(Map.of("trace", "abc"))
                .build();

        var copy = TaskUpdateRequest.builder().from(request).build();

        assertThat(TaskUpdateRequest.class).isInterface();
        assertThat(copy.taskId()).isEqualTo("task-1");
        assertThat(copy.inputResponses()).containsEntry("approved", true);
        assertThat(copy.meta()).containsEntry("trace", "abc");
    }

    @Test
    void buildsMethodSpecificTaskRequests() {
        var get = TaskGetRequest.builder()
                .taskId("task-1")
                .meta(Map.of("trace", "get"))
                .build();
        var cancel = TaskCancelRequest.builder()
                .taskId("task-1")
                .meta(Map.of("trace", "cancel"))
                .build();
        var list = TaskListRequest.builder()
                .limit(25)
                .cursor("next")
                .meta(Map.of("trace", "list"))
                .build();
        var awaitResult = TaskAwaitResultRequest.builder()
                .taskId("task-1")
                .meta(Map.of("trace", "result"))
                .build();

        assertThat(get.taskId()).isEqualTo("task-1");
        assertThat(cancel.meta()).containsEntry("trace", "cancel");
        assertThat(list.limit()).isEqualTo(25);
        assertThat(list.cursor()).isEqualTo("next");
        assertThat(awaitResult.meta()).containsEntry("trace", "result");
    }

    @Test
    void rejectsInvalidTaskTimingAndPayloadCombinations() {
        var working = TaskSnapshot.working("task-5", Instant.EPOCH, 1);

        assertThatThrownBy(() -> TaskSnapshot.builder()
                        .from(working)
                        .lastUpdatedAt(Instant.EPOCH.minusSeconds(1))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastUpdatedAt");
        assertThatThrownBy(() -> TaskSnapshot.builder()
                        .from(working)
                        .ttl(Duration.ofMillis(-1))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");
        assertThatThrownBy(() -> TaskSnapshot.builder()
                        .from(working)
                        .pollInterval(Duration.ZERO)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pollInterval");
    }

    @Test
    void connectorRequiresEveryModernTaskOperation() {
        var builder = TaskConnector.builder().get((context, request) -> null);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancel");

        builder.cancel((context, request) -> {});
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("update");

        assertThat(builder.update((context, request) -> {}).build()).isNotNull();
    }
}
