/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.ServerFeature;
import dev.tachyonmcp.api.server.domain.ContentBlock;
import dev.tachyonmcp.api.server.domain.InputRequestBundle;
import dev.tachyonmcp.api.server.domain.ProgressToken;
import dev.tachyonmcp.api.server.domain.Task;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.features.tasks.TaskDescriptor;
import dev.tachyonmcp.api.server.features.tasks.TaskState;
import dev.tachyonmcp.core.server.config.TasksConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

@InternalApi
public class TaskEntry implements ServerFeature<TaskDescriptor>, Task {

    private static final Duration MAX_MILLIS_DURATION = Duration.ofMillis(Long.MAX_VALUE);

    private final TaskDescriptor descriptor;
    private final String id;
    private final @Nullable String sessionId;
    private final @Nullable Map<String, Object> meta;
    private final AtomicReference<TaskState> status;
    private final long createdAt;
    private final @Nullable Duration ttl;
    private final Duration keepAlive;
    private final @Nullable Duration pollInterval;
    private volatile long lastUpdatedAt;
    private volatile long expiredAt;
    private volatile @Nullable String statusMessage;
    private volatile @Nullable InputRequestBundle pendingInput;
    private final ReentrantLock inputResponsesLock = new ReentrantLock();
    /**
     * @implNote guarded by {@link #inputResponsesLock}
     */
    private @Nullable Map<String, Object> pendingInputResponses;

    private final CompletableFuture<TaskResult> completionFuture = new CompletableFuture<>();
    private final @Nullable ProgressToken progressToken;
    private final Consumer<TaskEntry> statusListener;
    private final Clock clock;

    /**
     * Starts building a {@link TaskEntry}. {@code status} defaults to {@link TaskState#WORKING}
     * and {@code keepAlive} to {@link TasksConfig#DEFAULT_TASK_KEEP_ALIVE}; every other field
     * defaults to {@code null}/absent.
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Builds a {@link TaskEntry}. See {@link #builder(String)}.
     */
    public static final class Builder {
        private final String id;
        private TaskState status = TaskState.WORKING;
        private @Nullable Duration ttl;
        private @Nullable String sessionId;
        private @Nullable ProgressToken progressToken;
        private @Nullable Map<String, Object> meta;
        private Duration keepAlive = TasksConfig.DEFAULT_TASK_KEEP_ALIVE;
        private @Nullable Duration pollInterval;
        private Consumer<TaskEntry> statusListener = entry -> {};
        private Clock clock = Clock.systemUTC();

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder status(TaskState status) {
            this.status = Objects.requireNonNull(status, "status");
            return this;
        }

        public Builder ttl(@Nullable Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder sessionId(@Nullable String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder progressToken(@Nullable ProgressToken progressToken) {
            this.progressToken = progressToken;
            return this;
        }

        public Builder meta(@Nullable Map<String, Object> meta) {
            this.meta = meta;
            return this;
        }

        public Builder keepAlive(Duration keepAlive) {
            this.keepAlive = Objects.requireNonNull(keepAlive, "keepAlive");
            return this;
        }

        public Builder pollInterval(@Nullable Duration pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        /**
         * Invoked with the entry after every successful status/message mutation. Package-private:
         * only {@link DefaultTaskRegistry} wires a non-default listener.
         */
        Builder statusListener(Consumer<TaskEntry> statusListener) {
            this.statusListener = Objects.requireNonNull(statusListener, "statusListener");
            return this;
        }

        /**
         * Overrides the {@link Clock} used for {@code createdAt}/{@code lastUpdatedAt} and
         * TTL/expiry checks. Package-private: only {@link DefaultTaskRegistry} wires a clock
         * other than the system default.
         */
        Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public TaskEntry build() {
            return new TaskEntry(
                    id, status, ttl, sessionId, progressToken, meta, keepAlive, pollInterval, statusListener, clock);
        }
    }

    private TaskEntry(
            String id,
            TaskState status,
            @Nullable Duration ttl,
            @Nullable String sessionId,
            @Nullable ProgressToken progressToken,
            @Nullable Map<String, Object> meta,
            Duration keepAlive,
            @Nullable Duration pollInterval,
            Consumer<TaskEntry> statusListener,
            Clock clock) {
        this.descriptor = TaskDescriptor.builder().id(id).build();
        this.id = id;
        this.sessionId = sessionId;
        this.meta = meta;
        this.status = new AtomicReference<>(status);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.createdAt = clock.millis();
        this.lastUpdatedAt = this.createdAt;
        this.ttl = normalizeTtl(ttl);
        this.keepAlive = Objects.requireNonNull(keepAlive, "keepAlive");
        this.pollInterval = pollInterval;
        this.progressToken = progressToken;
        this.statusListener = Objects.requireNonNull(statusListener, "statusListener");
    }

    /**
     * The session that created this task, or {@code null} for programmatic/server-global tasks.
     */
    public @Nullable String sessionId() {
        return sessionId;
    }

    public @Nullable ProgressToken progressToken() {
        return progressToken;
    }

    @Override
    public @Nullable Map<String, Object> meta() {
        return meta;
    }

    @Override
    public TaskDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public TaskState status() {
        return status.get();
    }

    @Override
    public @Nullable String statusMessage() {
        return statusMessage;
    }

    @Override
    public Instant createdAt() {
        return Instant.ofEpochMilli(createdAt);
    }

    @Override
    public @Nullable Duration ttl() {
        return ttl;
    }

    /**
     * The normalized TTL in milliseconds, or {@code null} if this task never expires.
     */
    public @Nullable Long ttlMillis() {
        return ttl != null ? ttl.toMillis() : null;
    }

    /**
     * Zero and negative durations mean "never expires", same as {@code null}. Durations longer
     * than {@link Long#MAX_VALUE} milliseconds are clamped rather than left to overflow {@link
     * Duration#toMillis()} wherever the TTL is later read in millis (wire encoding, {@link
     * #ttlMillis()}).
     */
    private static @Nullable Duration normalizeTtl(@Nullable Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return null;
        }
        return ttl.compareTo(MAX_MILLIS_DURATION) > 0 ? MAX_MILLIS_DURATION : ttl;
    }

    /**
     * How long after this task reaches a terminal state its result stays retrievable.
     */
    public Duration keepAlive() {
        return keepAlive;
    }

    @Override
    public @Nullable Duration pollInterval() {
        return pollInterval;
    }

    @Override
    public @Nullable TaskResult result() {
        return completionFuture.isDone() && !completionFuture.isCompletedExceptionally()
                ? completionFuture.join()
                : null;
    }

    @Override
    public CompletionStage<TaskResult> completion() {
        return completionFuture;
    }

    @Override
    public boolean start(@Nullable String statusMessage) {
        return transitionTo(TaskState.WORKING, null, statusMessage, null, TaskState.SUBMITTED);
    }

    @Override
    public boolean complete(TaskResult.Completed result) {
        return transitionTo(TaskState.COMPLETED, result);
    }

    @Override
    public boolean fail(TaskResult.Failed result) {
        return transitionTo(TaskState.FAILED, result);
    }

    @Override
    public boolean cancel(@Nullable String statusMessage) {
        if (!transitionTo(TaskState.CANCELLED, null, statusMessage)) {
            return false;
        }
        completionFuture.completeExceptionally(
                new IllegalStateException("Task cancelled" + (statusMessage != null ? ": " + statusMessage : "")));
        return true;
    }

    @Override
    public boolean requireInput(InputRequestBundle request, @Nullable String statusMessage) {
        Objects.requireNonNull(request, "request");
        return transitionTo(TaskState.INPUT_REQUIRED, null, statusMessage, request, null);
    }

    /**
     * Returns the requested inputs while this task is awaiting a response, or {@code null} when
     * the task isn't currently in the {@link TaskState#INPUT_REQUIRED} state.
     */
    public @Nullable InputRequestBundle pendingInput() {
        return status() == TaskState.INPUT_REQUIRED ? pendingInput : null;
    }

    /**
     * The merged, filtered input responses and opaque request state for a resumed round.
     */
    public record ResumeInputs(
            Map<String, Object> inputResponses, @Nullable String requestState) {}

    /**
     * Merges {@code responses} into this {@link TaskState#INPUT_REQUIRED} pause's accumulated
     * answers, filtered to keys present in {@link #pendingInput()}'s inputRequests and not already
     * answered — unknown or already-satisfied keys are silently dropped (per the {@code
     * tasks/update} spec). Per-round only: the accumulator is reset whenever a new {@code
     * INPUT_REQUIRED} round begins (see {@link #transitionTo(TaskState, TaskResult, String,
     * InputRequestBundle, TaskState)}), so a later round's resumer never sees an earlier round's
     * answers.
     *
     * <p>Returns the full merged set once every currently-outstanding key has an answer — this
     * call has also atomically resumed the task to {@link TaskState#WORKING} at that point — or
     * {@code null} when the task isn't awaiting input, or when outstanding keys remain.
     */
    public @Nullable ResumeInputs submitInput(Map<String, Object> responses) {
        inputResponsesLock.lock();
        try {
            var pending = pendingInput();
            if (pending == null) return null;
            var next = pendingInputResponses == null
                    ? new LinkedHashMap<String, Object>()
                    : new LinkedHashMap<>(pendingInputResponses);
            responses.forEach((key, value) -> {
                if (pending.inputRequests().containsKey(key) && !next.containsKey(key)) {
                    next.put(key, value);
                }
            });
            pendingInputResponses = next;
            if (!next.keySet().containsAll(pending.inputRequests().keySet())) {
                return null; // still waiting on more
            }
            // Not Map.copyOf: JSON null is a legal response value and Map.copyOf rejects nulls.
            var merged = Collections.unmodifiableMap(new LinkedHashMap<>(next));
            // Transition while still holding the lock: transitionTo's pause bookkeeping is guarded by
            // the same (reentrant) lock, so a new INPUT_REQUIRED round can't be installed between
            // validating this round's answers and resuming it -- otherwise a late resume could
            // clobber a fresher pause with a stale round's answers.
            if (!transitionTo(TaskState.WORKING, null, "Input received")) return null;
            return new ResumeInputs(merged, pending.requestState());
        } finally {
            inputResponsesLock.unlock();
        }
    }

    @Override
    public boolean updateMessage(String statusMessage) {
        Objects.requireNonNull(statusMessage, "statusMessage");
        var current = status.get();
        if (current == TaskState.WORKING || current == TaskState.INPUT_REQUIRED) {
            this.statusMessage = statusMessage;
            this.lastUpdatedAt = clock.millis();
            statusListener.accept(this);
            return true;
        }
        return false;
    }

    @Override
    public void reportProgress(double progress, @Nullable Double total, @Nullable String message) {}

    @Override
    public Instant lastUpdatedAt() {
        return Instant.ofEpochMilli(lastUpdatedAt);
    }

    public @Nullable String resultJson() {
        var result = result();
        if (result instanceof TaskResult.Completed c) {
            return serializeResult(c.content(), c.structuredContent());
        }
        if (result instanceof TaskResult.Failed f) {
            if (f.protocolError() != null) {
                return f.protocolError().message();
            }
            return serializeResult(f.content(), f.structuredContent());
        }
        return null;
    }

    private static String serializeResult(List<ContentBlock> content, @Nullable Object structured) {
        if (structured != null) {
            return structured.toString();
        }
        if (!content.isEmpty() && content.getFirst() instanceof TextContent tc) {
            return tc.text();
        }
        return "{}";
    }

    /**
     * Transitions to {@code newStatus} without a result value.
     */
    public boolean transitionTo(TaskState newStatus) {
        return transitionTo(newStatus, null, null);
    }

    /**
     * Transitions to {@code newStatus}, publishing {@code result} (when non-null).
     */
    public boolean transitionTo(TaskState newStatus, @Nullable TaskResult result) {
        return transitionTo(newStatus, result, null);
    }

    /**
     * Transitions to {@code newStatus}, publishing {@code result} and {@code statusMessage}
     * (when non-null) atomically with the state change, then notifying the status listener.
     */
    boolean transitionTo(TaskState newStatus, @Nullable TaskResult result, @Nullable String statusMessage) {
        return transitionTo(newStatus, result, statusMessage, null, null);
    }

    /**
     * Transitions to {@code newStatus}, publishing {@code result}, {@code statusMessage} (when
     * non-null), and {@code pendingInput} atomically with the state change, then notifying the
     * status listener. {@code pendingInput} is only retained for {@link TaskState#INPUT_REQUIRED};
     * any other target status clears it, so a task never carries a stale bundle once it has moved
     * on, and the listener never observes {@code INPUT_REQUIRED} without its bundle already set.
     *
     * <p>A non-null {@code expected} narrows the move to that single source state, on top of the
     * {@link TaskState#canTransitionTo} table. {@link #start(String)} needs this: {@code
     * INPUT_REQUIRED -> WORKING} is a legal transition in general, but must only ever happen
     * through {@link #submitInput(Map)}.
     */
    private boolean transitionTo(
            TaskState newStatus,
            @Nullable TaskResult result,
            @Nullable String statusMessage,
            @Nullable InputRequestBundle pendingInput,
            @Nullable TaskState expected) {
        Objects.requireNonNull(newStatus, "status is required");
        if (newStatus == TaskState.COMPLETED) {
            Objects.requireNonNull(result, "result is required when transitioning to completed status");
        }
        var current = status.get();
        if (expected != null && current != expected) {
            return false;
        }
        if (!current.canTransitionTo(newStatus)) {
            return false;
        }
        if (status.compareAndSet(current, newStatus)) {
            this.lastUpdatedAt = clock.millis();
            if (statusMessage != null) {
                this.statusMessage = statusMessage;
            }
            inputResponsesLock.lock();
            try {
                this.pendingInput = newStatus == TaskState.INPUT_REQUIRED ? pendingInput : null;
                // Every transition starts a clean accumulator -- per-round, never cumulative.
                this.pendingInputResponses = null;
            } finally {
                inputResponsesLock.unlock();
            }
            if (newStatus.isTerminal()) {
                this.expiredAt = computeExpiredAt(this.lastUpdatedAt);
                if (result != null) {
                    completionFuture.complete(result);
                } else {
                    completionFuture.completeExceptionally(
                            new IllegalStateException("Terminal state reached without result"));
                }
            }
            statusListener.accept(this);
            return true;
        }
        return false;
    }

    public boolean isExpired() {
        var millis = ttlMillis();
        return millis != null && clock.millis() - lastUpdatedAt > millis;
    }

    /**
     * Whether this task's result has outlived its {@code keepAlive} retention window.
     */
    public boolean isResultExpired() {
        var deadline = expiredAt;
        return deadline != 0 && clock.millis() > deadline;
    }

    /**
     * Computes the absolute deadline at which the result expires, given the instant the task
     * became terminal. {@code keepAlive <= 0} means "never expires" ({@link Long#MAX_VALUE}).
     * Computed once at the terminal transition rather than on every {@link #isResultExpired()}
     * call — {@code expiredAt} isn't exposed by the protocol, so there's no need to keep the raw
     * terminal timestamp around.
     */
    private long computeExpiredAt(long terminalAtMillis) {
        if (keepAlive.isNegative() || keepAlive.isZero()) {
            return Long.MAX_VALUE;
        }
        return terminalAtMillis + keepAlive.toMillis();
    }
}
