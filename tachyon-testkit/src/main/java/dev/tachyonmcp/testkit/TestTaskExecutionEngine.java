/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.features.PaginatedResult;
import dev.tachyonmcp.api.server.features.tasks.LegacyTaskExecutionEngine;
import dev.tachyonmcp.api.server.features.tasks.TaskExecutionEngine;
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
public final class TestTaskExecutionEngine implements LegacyTaskExecutionEngine {

    private final Set<TaskFeature> supportedFeatures;
    private final Map<String, TaskSnapshot> snapshots = new ConcurrentHashMap<>();
    private final List<String> refreshedTaskIds = new CopyOnWriteArrayList<>();
    private final List<String> cancelledTaskIds = new CopyOnWriteArrayList<>();
    private final List<ListRequest> listRequests = new CopyOnWriteArrayList<>();
    private final List<String> awaitedTaskIds = new CopyOnWriteArrayList<>();
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

    /** Removes stored snapshots and captured calls. */
    public TestTaskExecutionEngine reset() {
        snapshots.clear();
        refreshedTaskIds.clear();
        cancelledTaskIds.clear();
        listRequests.clear();
        awaitedTaskIds.clear();
        submittedInputs.clear();
        return this;
    }

    @Override
    public Set<TaskFeature> supportedFeatures() {
        return supportedFeatures;
    }

    @Override
    public @Nullable TaskSnapshot refresh(InteractionContext context, String taskId) {
        refreshedTaskIds.add(taskId);
        return snapshots.get(taskId);
    }

    @Override
    public TaskSnapshot cancel(InteractionContext context, String taskId) {
        cancelledTaskIds.add(taskId);
        var current = requireSnapshot(taskId);
        var cancelled = TaskSnapshot.builder()
                .from(current)
                .status(dev.tachyonmcp.api.server.features.tasks.TaskState.CANCELLED)
                .lastUpdatedAt(java.time.Instant.now())
                .revision(current.revision() + 1)
                .build();
        snapshots.put(taskId, cancelled);
        return cancelled;
    }

    @Override
    public void submitInput(InteractionContext context, String taskId, TaskInput input) {
        submittedInputs.add(new SubmittedInput(taskId, input));
    }

    @Override
    public PaginatedResult<TaskSnapshot> list(InteractionContext context, int limit, @Nullable String cursor) {
        listRequests.add(new ListRequest(limit, cursor));
        var ordered = snapshots.values().stream()
                .sorted(java.util.Comparator.comparing(TaskSnapshot::taskId))
                .toList();
        var start = 0;
        if (cursor != null) {
            start = java.util.stream.IntStream.range(0, ordered.size())
                            .filter(index -> ordered.get(index).taskId().equals(cursor))
                            .findFirst()
                            .orElse(-2)
                    + 1;
            if (start == -1) {
                return PaginatedResult.of(List.of(), null, false);
            }
        }
        var end = Math.min(start + limit, ordered.size());
        var nextCursor = end < ordered.size() ? ordered.get(end - 1).taskId() : null;
        return PaginatedResult.of(ordered.subList(start, end), nextCursor, true);
    }

    @Override
    public TaskSnapshot awaitResult(InteractionContext context, String taskId) {
        awaitedTaskIds.add(taskId);
        return requireSnapshot(taskId);
    }

    /** Returns task IDs passed to {@link #refresh}. */
    public List<String> refreshedTaskIds() {
        return List.copyOf(refreshedTaskIds);
    }

    /** Returns task IDs passed to {@link #cancel}. */
    public List<String> cancelledTaskIds() {
        return List.copyOf(cancelledTaskIds);
    }

    /** Returns task-list requests received by this fixture. */
    public List<ListRequest> listRequests() {
        return List.copyOf(listRequests);
    }

    /** Returns task IDs passed to {@link #awaitResult}. */
    public List<String> awaitedTaskIds() {
        return List.copyOf(awaitedTaskIds);
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

    /** Captured legacy task-list request. */
    public record ListRequest(int limit, @Nullable String cursor) {}
}
