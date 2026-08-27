/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.features.tasks.TaskExecutionEngine;
import dev.tachyonmcp.api.server.features.tasks.TaskExecutionRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskFeature;
import dev.tachyonmcp.api.server.features.tasks.TaskInput;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/** Controllable {@link TaskExecutionEngine} fixture for MCP server tests. */
public final class TestTaskExecutionEngine implements TaskExecutionEngine {

    private final Set<TaskFeature> supportedFeatures;
    private final Map<String, TaskSnapshot> snapshots = new ConcurrentHashMap<>();
    private final List<String> refreshedTaskIds = new CopyOnWriteArrayList<>();
    private final List<TaskExecutionRequest> executionRequests = new CopyOnWriteArrayList<>();
    private final List<String> cancelledTaskIds = new CopyOnWriteArrayList<>();
    private final List<SubmittedInput> submittedInputs = new CopyOnWriteArrayList<>();

    /** Creates a fixture supporting all optional task operations. */
    public TestTaskExecutionEngine() {
        this(Set.of(TaskFeature.values()));
    }

    /** Creates a fixture supporting the supplied optional task operations. */
    public TestTaskExecutionEngine(Set<TaskFeature> supportedFeatures) {
        this.supportedFeatures = Set.copyOf(supportedFeatures);
    }

    /** Stores the snapshot returned by subsequent engine operations. */
    public TestTaskExecutionEngine publish(TaskSnapshot snapshot) {
        snapshots.put(snapshot.taskId(), Objects.requireNonNull(snapshot, "snapshot"));
        return this;
    }

    @Override
    public Set<TaskFeature> supportedFeatures() {
        return supportedFeatures;
    }

    @Override
    public TaskSnapshot start(InteractionContext context, TaskExecutionRequest request) {
        executionRequests.add(request);
        return requireSnapshot(request.taskId());
    }

    @Override
    public @Nullable TaskSnapshot refresh(InteractionContext context, String taskId) {
        refreshedTaskIds.add(taskId);
        return snapshots.get(taskId);
    }

    @Override
    public TaskSnapshot cancel(InteractionContext context, String taskId) {
        cancelledTaskIds.add(taskId);
        return requireSnapshot(taskId);
    }

    @Override
    public void submitInput(InteractionContext context, String taskId, TaskInput input) {
        submittedInputs.add(new SubmittedInput(taskId, input));
    }

    /** Returns task IDs passed to {@link #refresh}. */
    public List<String> refreshedTaskIds() {
        return List.copyOf(refreshedTaskIds);
    }

    /** Returns execution requests received by this fixture. */
    public List<TaskExecutionRequest> executionRequests() {
        return List.copyOf(executionRequests);
    }

    /** Returns task IDs passed to {@link #cancel}. */
    public List<String> cancelledTaskIds() {
        return List.copyOf(cancelledTaskIds);
    }

    /** Returns input submissions received by this fixture. */
    public List<SubmittedInput> submittedInputs() {
        return List.copyOf(submittedInputs);
    }

    private TaskSnapshot requireSnapshot(String taskId) {
        var snapshot = snapshots.get(taskId);
        if (snapshot == null) {
            throw new IllegalArgumentException("Unknown task: " + taskId);
        }
        return snapshot;
    }

    /** Captured {@code tasks/update} submission. */
    public record SubmittedInput(String taskId, TaskInput input) {
        /** Creates a captured input submission. */
        public SubmittedInput {
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(input, "input");
        }
    }
}
