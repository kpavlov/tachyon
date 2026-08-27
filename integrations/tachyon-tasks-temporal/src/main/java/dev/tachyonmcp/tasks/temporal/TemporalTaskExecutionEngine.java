/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.tasks.temporal;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.features.tasks.TaskExecutionEngine;
import dev.tachyonmcp.api.server.features.tasks.TaskExecutionRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskFeature;
import dev.tachyonmcp.api.server.features.tasks.TaskInput;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Task execution engine backed by one Temporal Workflow Execution per MCP task. */
@ExperimentalApi
public final class TemporalTaskExecutionEngine implements TaskExecutionEngine {

    private static final Logger logger = LoggerFactory.getLogger(TemporalTaskExecutionEngine.class);
    private static final Set<TaskFeature> FEATURES = Set.of(TaskFeature.CANCEL, TaskFeature.REQUESTS);

    private final WorkflowClient workflowClient;
    private final String taskQueue;
    private final Clock clock;
    private final Map<String, TemporalTaskRoute<?>> routesByOperation;
    private final Map<String, TemporalTaskRoute<?>> routesByWorkflowType;

    private TemporalTaskExecutionEngine(Builder builder) {
        workflowClient = Objects.requireNonNull(builder.workflowClient, "workflowClient");
        taskQueue = requireText(builder.taskQueue, "taskQueue");
        clock = Objects.requireNonNull(builder.clock, "clock");
        if (builder.routesByOperation.isEmpty()) {
            throw new IllegalStateException("At least one Temporal task route is required");
        }
        routesByOperation = Map.copyOf(builder.routesByOperation);
        routesByWorkflowType = Map.copyOf(builder.routesByWorkflowType);
    }

    /**
     * Creates an engine builder.
     *
     * @param workflowClient Temporal client used to start and address Workflows
     * @return a new builder
     */
    public static Builder builder(WorkflowClient workflowClient) {
        return new Builder(workflowClient);
    }

    @Override
    public Set<TaskFeature> supportedFeatures() {
        return FEATURES;
    }

    @Override
    public TaskSnapshot start(InteractionContext context, TaskExecutionRequest request) {
        var route = routeForOperation(request.operation());
        logger.info(
                "Starting Temporal workflow: taskId={}, operation={}, workflowType={}, taskQueue={}",
                request.taskId(),
                request.operation(),
                route.workflowType(),
                taskQueue);
        var workflow = workflowClient.newUntypedWorkflowStub(
                route.workflowType(),
                WorkflowOptions.newBuilder()
                        .setWorkflowId(request.taskId())
                        .setTaskQueue(taskQueue)
                        .build());
        workflow.start(route.startArguments(request));
        return TaskSnapshot.working(request.taskId(), clock.instant(), 1);
    }

    @Override
    public TaskSnapshot refresh(InteractionContext context, String taskId) {
        var workflow = workflow(taskId);
        var snapshot = routeFor(workflow).query(workflow, taskId);
        logger.debug(
                "Refreshed Temporal workflow: taskId={}, state={}, revision={}",
                taskId,
                snapshot.status(),
                snapshot.revision());
        return snapshot;
    }

    @Override
    public TaskSnapshot cancel(InteractionContext context, String taskId) {
        var workflow = workflow(taskId);
        var status = routeFor(workflow).query(workflow, taskId);
        logger.info("Cancelling Temporal workflow: taskId={}, state={}", taskId, status.status());
        workflow.cancel();
        return TaskSnapshot.builder()
                .taskId(taskId)
                .status(TaskState.CANCELLED)
                .statusMessage("Cancelled")
                .createdAt(status.createdAt())
                .lastUpdatedAt(clock.instant())
                .revision(status.revision() + 1)
                .build();
    }

    @Override
    public void submitInput(InteractionContext context, String taskId, TaskInput input) {
        var workflow = workflow(taskId);
        logger.info(
                "Submitting input to Temporal workflow: taskId={}, fields={}",
                taskId,
                input.inputResponses().keySet());
        routeFor(workflow).submitInput(workflow, input);
    }

    private WorkflowStub workflow(String taskId) {
        return workflowClient.newUntypedWorkflowStub(taskId);
    }

    private TemporalTaskRoute<?> routeForOperation(String operation) {
        var route = routesByOperation.get(operation);
        if (route == null) {
            throw new IllegalArgumentException("No Temporal route for operation: " + operation);
        }
        return route;
    }

    private TemporalTaskRoute<?> routeFor(WorkflowStub workflow) {
        var workflowType = workflow.describe().getWorkflowType();
        var route = routesByWorkflowType.get(workflowType);
        if (route == null) {
            throw new IllegalStateException("No Temporal task route for Workflow type: " + workflowType);
        }
        return route;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must not be blank");
        }
        return value;
    }

    /** Builder for {@link TemporalTaskExecutionEngine}. */
    public static final class Builder {

        private final WorkflowClient workflowClient;
        private final Map<String, TemporalTaskRoute<?>> routesByOperation = new LinkedHashMap<>();
        private final Map<String, TemporalTaskRoute<?>> routesByWorkflowType = new LinkedHashMap<>();
        private String taskQueue;
        private Clock clock = Clock.systemUTC();

        private Builder(WorkflowClient workflowClient) {
            this.workflowClient = Objects.requireNonNull(workflowClient, "workflowClient");
        }

        /**
         * Sets the task queue used when starting Workflows.
         *
         * @param taskQueue worker task queue
         * @return this builder
         */
        public Builder taskQueue(String taskQueue) {
            this.taskQueue = taskQueue;
            return this;
        }

        /**
         * Registers one operation-to-Workflow route.
         *
         * @param route route to register
         * @return this builder
         */
        public Builder route(TemporalTaskRoute<?> route) {
            Objects.requireNonNull(route, "route");
            putUnique(routesByOperation, route.operation(), route, "operation");
            putUnique(routesByWorkflowType, route.workflowType(), route, "Workflow type");
            return this;
        }

        Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Builds the Temporal task execution engine.
         *
         * @return configured engine
         */
        public TemporalTaskExecutionEngine build() {
            return new TemporalTaskExecutionEngine(this);
        }

        private static void putUnique(
                Map<String, TemporalTaskRoute<?>> routes, String key, TemporalTaskRoute<?> route, String keyType) {
            if (routes.putIfAbsent(key, route) != null) {
                throw new IllegalArgumentException("Duplicate Temporal task route " + keyType + ": " + key);
            }
        }
    }
}
