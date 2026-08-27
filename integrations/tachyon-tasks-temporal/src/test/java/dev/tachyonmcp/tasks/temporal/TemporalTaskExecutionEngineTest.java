/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.tasks.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.tasks.TaskExecutionRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskInput;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemporalTaskExecutionEngineTest {

    private static final String TASK_QUEUE = "tachyon-tasks-temporal-test";
    private static final InteractionContext CONTEXT = mock(InteractionContext.class);

    @Test
    void reattachesToWorkflowAndForwardsInput() {
        try (var environment = TestWorkflowEnvironment.newInstance()) {
            var worker = environment.newWorker(TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(TestWorkflowImpl.class);
            environment.start();
            var engine = engine(environment);

            var started = engine.start(CONTEXT, request("workflow-1"));

            assertThat(started.status()).isEqualTo(TaskState.WORKING);
            var reattachedEngine = engine(environment);
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(reattachedEngine
                                    .refresh(CONTEXT, "workflow-1")
                                    .status())
                            .isEqualTo(TaskState.INPUT_REQUIRED));

            reattachedEngine.submitInput(
                    CONTEXT,
                    "workflow-1",
                    TaskInput.builder()
                            .inputResponses(Map.of("approved", true))
                            .requestState("approval-1")
                            .build());

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(reattachedEngine
                                    .refresh(CONTEXT, "workflow-1")
                                    .status())
                            .isEqualTo(TaskState.COMPLETED));
        }
    }

    private static TemporalTaskExecutionEngine engine(TestWorkflowEnvironment environment) {
        return TemporalTaskExecutionEngine.builder(environment.getWorkflowClient())
                .taskQueue(TASK_QUEUE)
                .route(TemporalTaskRoute.builder(TestStatus.class)
                        .operation("test_operation")
                        .workflowType("TestWorkflow")
                        .startArguments(
                                request -> new Object[] {request.arguments().asMap()})
                        .statusQuery("taskStatus")
                        .snapshotMapper(TemporalTaskExecutionEngineTest::snapshot)
                        .inputUpdate("provideInput", input -> new Object[] {input.inputResponses()})
                        .build())
                .build();
    }

    private static TaskSnapshot snapshot(String taskId, TestStatus status) {
        return TaskSnapshot.builder()
                .taskId(taskId)
                .status(status.state())
                .statusMessage(status.message())
                .createdAt(status.createdAt())
                .lastUpdatedAt(status.updatedAt())
                .result(status.state() == TaskState.COMPLETED ? TaskResult.completed(status.result()) : null)
                .revision(status.revision())
                .build();
    }

    private static TaskExecutionRequest request(String taskId) {
        return TaskExecutionRequest.builder()
                .taskId(taskId)
                .operation("test_operation")
                .arguments(JsonObject.of(Map.of("customer", "Ada")))
                .build();
    }

    @WorkflowInterface
    public interface TestWorkflow {

        @WorkflowMethod
        void run(Map<String, Object> arguments);

        @QueryMethod
        TestStatus taskStatus();

        @UpdateMethod
        void provideInput(Map<String, Object> input);
    }

    public static final class TestWorkflowImpl implements TestWorkflow {

        private Instant createdAt;
        private TaskState state = TaskState.SUBMITTED;
        private Map<String, Object> input;
        private long revision;

        @Override
        public void run(Map<String, Object> arguments) {
            createdAt = now();
            state = TaskState.INPUT_REQUIRED;
            revision++;
            Workflow.await(() -> input != null);
            state = TaskState.COMPLETED;
            revision++;
        }

        @Override
        public TestStatus taskStatus() {
            return new TestStatus(state, state.name(), createdAt, now(), input == null ? Map.of() : input, revision);
        }

        @Override
        public void provideInput(Map<String, Object> input) {
            this.input = input;
            revision++;
        }

        private static Instant now() {
            return Instant.ofEpochMilli(Workflow.currentTimeMillis());
        }
    }

    record TestStatus(
            TaskState state,
            String message,
            Instant createdAt,
            Instant updatedAt,
            Map<String, Object> result,
            long revision) {}
}
