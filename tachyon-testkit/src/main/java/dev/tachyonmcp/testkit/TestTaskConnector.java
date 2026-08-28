/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.features.PaginatedResult;
import dev.tachyonmcp.api.server.features.tasks.TaskAwaitResultRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskCancelRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskConnector;
import dev.tachyonmcp.api.server.features.tasks.TaskGetRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskListRequest;
import dev.tachyonmcp.api.server.features.tasks.TaskNotFoundException;
import dev.tachyonmcp.api.server.features.tasks.TaskSnapshot;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.api.server.features.tasks.TaskUpdateRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/** Controllable task-connector fixture for MCP server tests. */
public final class TestTaskConnector {

    private final Map<String, TaskSnapshot> snapshots = new ConcurrentHashMap<>();
    private final List<String> startedTaskIds = new CopyOnWriteArrayList<>();
    private final List<String> refreshedTaskIds = new CopyOnWriteArrayList<>();
    private final List<String> cancelledTaskIds = new CopyOnWriteArrayList<>();
    private final List<TaskListRequest> listRequests = new CopyOnWriteArrayList<>();
    private final List<String> awaitedTaskIds = new CopyOnWriteArrayList<>();
    private final List<TaskUpdateRequest> submittedInputs = new CopyOnWriteArrayList<>();
    private volatile boolean settleCancellation = true;

    /** Starts a task in the external system represented by this fixture. */
    public TestTaskConnector start(TaskSnapshot snapshot) {
        var validated = Objects.requireNonNull(snapshot, "snapshot");
        startedTaskIds.add(validated.taskId());
        snapshots.put(validated.taskId(), validated);
        return this;
    }

    /** Stores a later snapshot returned by subsequent connector operations. */
    public TestTaskConnector publish(TaskSnapshot snapshot) {
        var validated = Objects.requireNonNull(snapshot, "snapshot");
        snapshots.put(validated.taskId(), validated);
        return this;
    }

    /** Leaves the current snapshot unchanged when cancellation is requested. */
    public TestTaskConnector deferCancellation() {
        settleCancellation = false;
        return this;
    }

    /** Removes stored snapshots and captured calls. */
    public TestTaskConnector reset() {
        snapshots.clear();
        startedTaskIds.clear();
        refreshedTaskIds.clear();
        cancelledTaskIds.clear();
        listRequests.clear();
        awaitedTaskIds.clear();
        submittedInputs.clear();
        settleCancellation = true;
        return this;
    }

    /** Builds a connector wiring every operation this fixture supports, including legacy. */
    @SuppressWarnings("deprecation")
    public TaskConnector connector() {
        return TaskConnector.builder()
                .get(this::refresh)
                .cancel(this::cancel)
                .update(this::submitInput)
                .list(this::list)
                .awaitResult(this::awaitResult)
                .build();
    }

    /**
     * Implements {@code TaskGetFn}: looks up the stored snapshot for {@code request.taskId()}.
     * Records the lookup for {@link #refreshedTaskIds()}.
     *
     * @param context current MCP interaction
     * @param request task lookup request
     * @return the stored snapshot, or {@code null} if the task is unknown
     */
    public @Nullable TaskSnapshot refresh(InteractionContext context, TaskGetRequest request) {
        refreshedTaskIds.add(request.taskId());
        return snapshots.get(request.taskId());
    }

    /**
     * Implements {@code TaskCancelFn}: records the cancellation request and advances a
     * non-terminal snapshot to {@link TaskState#CANCELLED}, unless {@link #deferCancellation()}
     * was called or the snapshot is already terminal.
     *
     * @param context current MCP interaction
     * @param request task cancellation request
     * @throws TaskNotFoundException if {@code request.taskId()} has no stored snapshot
     */
    public void cancel(InteractionContext context, TaskCancelRequest request) throws TaskNotFoundException {
        var current = requireSnapshot(request.taskId());
        cancelledTaskIds.add(request.taskId());
        if (!settleCancellation || current.status().isTerminal()) {
            return;
        }
        var cancelled = TaskSnapshot.builder()
                .from(current)
                .status(TaskState.CANCELLED)
                .lastUpdatedAt(current.lastUpdatedAt())
                .revision(current.revision() + 1)
                .build();
        snapshots.put(request.taskId(), cancelled);
    }

    /**
     * Implements {@code TaskUpdateFn}: records the submitted input for later inspection via
     * {@link #submittedInputs()}.
     *
     * @param context current MCP interaction
     * @param request task input submission
     * @throws TaskNotFoundException if {@code request.taskId()} has no stored snapshot
     */
    public void submitInput(InteractionContext context, TaskUpdateRequest request) throws TaskNotFoundException {
        requireSnapshot(request.taskId());
        submittedInputs.add(request);
    }

    /**
     * Implements the legacy {@code TaskListFn}: returns stored snapshots ordered by task ID,
     * paginated per {@code request}.
     *
     * @param context current MCP interaction
     * @param request page size and optional cursor
     * @return the requested page of snapshots
     */
    @SuppressWarnings("deprecation")
    public PaginatedResult<TaskSnapshot> list(InteractionContext context, TaskListRequest request) {
        listRequests.add(request);
        var ordered = snapshots.values().stream()
                .sorted(Comparator.comparing(TaskSnapshot::taskId))
                .toList();
        var start = 0;
        if (request.cursor() != null) {
            start = IntStream.range(0, ordered.size())
                            .filter(index -> ordered.get(index).taskId().equals(request.cursor()))
                            .findFirst()
                            .orElse(-2)
                    + 1;
            if (start == -1) {
                return PaginatedResult.of(List.of(), null, false);
            }
        }
        var end = Math.min(start + request.limit(), ordered.size());
        var nextCursor = end < ordered.size() ? ordered.get(end - 1).taskId() : null;
        return PaginatedResult.of(ordered.subList(start, end), nextCursor, true);
    }

    /**
     * Implements the legacy blocking {@code TaskAwaitResultFn}: returns the current snapshot
     * immediately rather than actually blocking until the task reaches a terminal state.
     *
     * @param context current MCP interaction
     * @param request task lookup request
     * @return the stored snapshot
     * @throws TaskNotFoundException if {@code request.taskId()} has no stored snapshot
     */
    @SuppressWarnings("deprecation")
    public TaskSnapshot awaitResult(InteractionContext context, TaskAwaitResultRequest request)
            throws TaskNotFoundException {
        awaitedTaskIds.add(request.taskId());
        return requireSnapshot(request.taskId());
    }

    /** Returns task IDs passed to {@link #refresh}. */
    public List<String> refreshedTaskIds() {
        return List.copyOf(refreshedTaskIds);
    }

    /** Returns task IDs started by the application through {@link #start(TaskSnapshot)}. */
    public List<String> startedTaskIds() {
        return List.copyOf(startedTaskIds);
    }

    /** Returns task IDs passed to {@link #cancel}. */
    public List<String> cancelledTaskIds() {
        return List.copyOf(cancelledTaskIds);
    }

    /** Returns task-list requests received by this fixture. */
    @SuppressWarnings("deprecation")
    public List<TaskListRequest> listRequests() {
        return List.copyOf(listRequests);
    }

    /** Returns task IDs passed to {@link #awaitResult}. */
    public List<String> awaitedTaskIds() {
        return List.copyOf(awaitedTaskIds);
    }

    /** Returns input submissions received by this fixture. */
    public List<TaskUpdateRequest> submittedInputs() {
        return List.copyOf(submittedInputs);
    }

    private TaskSnapshot requireSnapshot(String taskId) throws TaskNotFoundException {
        var snapshot = snapshots.get(taskId);
        if (snapshot == null) {
            throw new TaskNotFoundException(taskId);
        }
        return snapshot;
    }
}
