/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.tasks.temporal;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import dev.tachyonmcp.api.server.features.tasks.TaskUpdateRequest;
import io.temporal.client.WorkflowStub;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Maps one Tachyon operation to one Temporal Workflow type and its task-facing messages.
 *
 * @param <S> application-owned workflow status type returned by the configured query
 */
@ExperimentalApi
public final class TemporalTaskRoute<S> {

    private final String operation;
    private final String workflowType;
    private final String statusQuery;
    private final String inputUpdate;
    private final Class<S> statusType;
    private final Function<TemporalTaskStartRequest, Object[]> startArguments;
    private final Function<TaskUpdateRequest, Object[]> inputArguments;
    private final BiFunction<String, S, TaskSnapshot> snapshotMapper;

    private TemporalTaskRoute(Builder<S> builder) {
        operation = requireText(builder.operation, "operation");
        workflowType = requireText(builder.workflowType, "workflowType");
        statusQuery = requireText(builder.statusQuery, "statusQuery");
        inputUpdate = requireText(builder.inputUpdate, "inputUpdate");
        statusType = Objects.requireNonNull(builder.statusType, "statusType");
        startArguments = Objects.requireNonNull(builder.startArguments, "startArguments");
        inputArguments = Objects.requireNonNull(builder.inputArguments, "inputArguments");
        snapshotMapper = Objects.requireNonNull(builder.snapshotMapper, "snapshotMapper");
    }

    /**
     * Creates a route builder.
     *
     * @param statusType application-owned workflow status type
     * @param <S> workflow status type
     * @return a new builder
     */
    public static <S> Builder<S> builder(Class<S> statusType) {
        return new Builder<>(statusType);
    }

    String operation() {
        return operation;
    }

    String workflowType() {
        return workflowType;
    }

    Object[] startArguments(TemporalTaskStartRequest request) {
        return Objects.requireNonNull(startArguments.apply(request), "startArguments result");
    }

    TaskSnapshot query(WorkflowStub workflow, String taskId) {
        var status = workflow.query(statusQuery, statusType);
        var snapshot = Objects.requireNonNull(snapshotMapper.apply(taskId, status), "snapshotMapper result");
        if (!taskId.equals(snapshot.taskId())) {
            throw new IllegalStateException("Temporal snapshot task ID does not match Workflow ID: " + taskId);
        }
        return snapshot;
    }

    void submitInput(WorkflowStub workflow, TaskUpdateRequest input) {
        var arguments = Objects.requireNonNull(inputArguments.apply(input), "inputArguments result");
        workflow.update(inputUpdate, Void.class, arguments);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must not be blank");
        }
        return value;
    }

    /**
     * Builder for {@link TemporalTaskRoute}.
     *
     * @param <S> application-owned workflow status type
     */
    public static final class Builder<S> {

        private final Class<S> statusType;
        private String operation;
        private String workflowType;
        private String statusQuery;
        private String inputUpdate;
        private Function<TemporalTaskStartRequest, Object[]> startArguments;
        private Function<TaskUpdateRequest, Object[]> inputArguments;
        private BiFunction<String, S, TaskSnapshot> snapshotMapper;

        private Builder(Class<S> statusType) {
            this.statusType = Objects.requireNonNull(statusType, "statusType");
        }

        /**
         * Sets the Tachyon operation routed to this Workflow type.
         *
         * @param operation operation name
         * @return this builder
         */
        public Builder<S> operation(String operation) {
            this.operation = operation;
            return this;
        }

        /**
         * Sets the Temporal Workflow type name registered by the worker.
         *
         * @param workflowType Workflow type name
         * @return this builder
         */
        public Builder<S> workflowType(String workflowType) {
            this.workflowType = workflowType;
            return this;
        }

        /**
         * Maps a task start request to Temporal Workflow arguments.
         *
         * @param startArguments argument mapper
         * @return this builder
         */
        public Builder<S> startArguments(Function<TemporalTaskStartRequest, Object[]> startArguments) {
            this.startArguments = startArguments;
            return this;
        }

        /**
         * Sets the Temporal Query name used to retrieve authoritative task status.
         *
         * @param statusQuery Query name
         * @return this builder
         */
        public Builder<S> statusQuery(String statusQuery) {
            this.statusQuery = statusQuery;
            return this;
        }

        /**
         * Maps application-owned workflow status to a complete Tachyon task snapshot.
         *
         * @param snapshotMapper status mapper
         * @return this builder
         */
        public Builder<S> snapshotMapper(BiFunction<String, S, TaskSnapshot> snapshotMapper) {
            this.snapshotMapper = snapshotMapper;
            return this;
        }

        /**
         * Sets the Temporal Update and maps submitted MCP input to its arguments.
         *
         * @param inputUpdate Update name
         * @param inputArguments argument mapper
         * @return this builder
         */
        public Builder<S> inputUpdate(String inputUpdate, Function<TaskUpdateRequest, Object[]> inputArguments) {
            this.inputUpdate = inputUpdate;
            this.inputArguments = inputArguments;
            return this;
        }

        /**
         * Builds the route.
         *
         * @return configured route
         */
        public TemporalTaskRoute<S> build() {
            return new TemporalTaskRoute<>(this);
        }
    }
}
