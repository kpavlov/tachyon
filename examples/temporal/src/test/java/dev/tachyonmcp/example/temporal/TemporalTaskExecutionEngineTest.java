/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.example.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.features.tasks.TaskExecutionRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskInput;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.tasks.temporal.TemporalTaskExecutionEngine;
import io.temporal.testing.TestWorkflowEnvironment;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemporalTaskExecutionEngineTest {

    private static final String TASK_QUEUE = "tachyon-temporal-test";
    private static final InteractionContext CONTEXT = mock(InteractionContext.class);

    @Test
    void startsRefreshesAndUpdatesWorkflowUsingTemporalTestEnvironment() {
        try (var testEnvironment = TestWorkflowEnvironment.newInstance()) {
            var worker = testEnvironment.newWorker(TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(BookingWorkflowImpl.class);
            testEnvironment.start();
            var engine = BookingTaskEngine.create(testEnvironment.getWorkflowClient(), TASK_QUEUE);

            var started = engine.start(CONTEXT, request("test-workflow"));

            assertThat(started.taskId()).isEqualTo("test-workflow");
            assertThat(started.status()).isEqualTo(TaskState.WORKING);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                            engine.refresh(CONTEXT, "test-workflow").status())
                    .isEqualTo(TaskState.INPUT_REQUIRED));

            engine.submitInput(
                    CONTEXT,
                    "test-workflow",
                    TaskInput.builder()
                            .inputResponses(Map.of("approved", true))
                            .requestState("approval-1")
                            .build());

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var completed = engine.refresh(CONTEXT, "test-workflow");
                assertThat(completed.status()).isEqualTo(TaskState.COMPLETED);
                assertThat(completed.result()).isNotNull();
            });
        }
    }

    private static TaskExecutionRequest request(String taskId) {
        return TaskExecutionRequest.builder()
                .taskId(taskId)
                .operation("book_appointment")
                .arguments(JsonObject.of(Map.of("customer", "Ada")))
                .build();
    }
}
