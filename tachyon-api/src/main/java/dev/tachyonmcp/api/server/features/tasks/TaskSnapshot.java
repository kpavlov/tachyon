/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.server.domain.HasMeta;
import dev.tachyonmcp.api.server.domain.InputRequestBundle;
import dev.tachyonmcp.api.server.domain.TaskResult;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/** Immutable MCP projection of externally executed work. */
@ExperimentalApi
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE, typeImmutable = "Default*")
public interface TaskSnapshot extends HasMeta {

    /** Returns the stable task identifier. */
    String taskId();

    /** Returns the current task state. */
    TaskState status();

    /** Returns an optional human-readable state description. */
    @Nullable
    String statusMessage();

    /** Returns the task creation timestamp. */
    Instant createdAt();

    /** Returns the latest state observation timestamp. */
    Instant lastUpdatedAt();

    /**
     * Returns the optional duration, measured from {@link #createdAt()}, after which the receiver
     * may delete this task and its result regardless of status. {@code null} means unlimited
     * retention. Not the same as a server's internal cache eviction policy, which may retain (or
     * evict) terminal snapshots on its own schedule.
     */
    @Nullable
    Duration ttl();

    /** Returns the optional suggested client polling interval. */
    @Nullable
    Duration pollInterval();

    /** Returns input currently required from the client. */
    @Nullable
    InputRequestBundle pendingInput();

    /** Returns the terminal result. */
    @Nullable
    TaskResult result();

    /** Returns optional protocol metadata. */
    @Override
    @Nullable
    Map<String, Object> meta();

    /** Returns the monotonically increasing projection revision. */
    long revision();

    /**
     * Validates task identity, revision, and that {@link #status()} agrees with {@link #result()}
     * and {@link #pendingInput()} — the combination the wire mapper serializes into a discriminated
     * union, so an inconsistent combination here would otherwise reach the wire.
     */
    @Value.Check
    default void check() {
        if (taskId().isBlank()) {
            throw new IllegalArgumentException("taskId cannot be blank");
        }
        if (revision() < 0) {
            throw new IllegalArgumentException("revision cannot be negative: " + revision());
        }
        if (lastUpdatedAt().isBefore(createdAt())) {
            throw new IllegalArgumentException("lastUpdatedAt cannot be before createdAt");
        }
        if (ttl() != null && ttl().isNegative()) {
            throw new IllegalArgumentException("ttl cannot be negative: " + ttl());
        }
        if (pollInterval() != null && (pollInterval().isZero() || pollInterval().isNegative())) {
            throw new IllegalArgumentException("pollInterval must be positive: " + pollInterval());
        }
        if (status() != TaskState.INPUT_REQUIRED && pendingInput() != null) {
            throw new IllegalArgumentException(status() + " snapshot cannot carry pendingInput");
        }
        switch (status()) {
            case COMPLETED -> {
                if (!(result() instanceof TaskResult.Completed)) {
                    throw new IllegalArgumentException("COMPLETED snapshot requires a TaskResult.Completed result");
                }
            }
            case FAILED, REJECTED -> {
                if (!(result() instanceof TaskResult.Failed)) {
                    throw new IllegalArgumentException(status() + " snapshot requires a TaskResult.Failed result");
                }
            }
            case CANCELLED, WORKING, SUBMITTED, AUTH_REQUIRED, UNKNOWN -> {
                if (result() != null) {
                    throw new IllegalArgumentException(status() + " snapshot cannot carry a result");
                }
            }
            case INPUT_REQUIRED -> {
                if (pendingInput() == null) {
                    throw new IllegalArgumentException("INPUT_REQUIRED snapshot requires pendingInput");
                }
                if (result() != null) {
                    throw new IllegalArgumentException("INPUT_REQUIRED snapshot cannot carry a result");
                }
            }
        }
    }

    /** Creates a working snapshot observed at the supplied instant. */
    static TaskSnapshot working(String taskId, Instant observedAt, long revision) {
        return builder()
                .taskId(taskId)
                .status(TaskState.WORKING)
                .createdAt(observedAt)
                .lastUpdatedAt(observedAt)
                .revision(revision)
                .build();
    }

    /** Creates a builder for a task snapshot. */
    static Builder builder() {
        return DefaultTaskSnapshot.builder();
    }

    /** Builder for {@link TaskSnapshot}. */
    interface Builder {
        /** Copies values from an existing snapshot. */
        Builder from(TaskSnapshot snapshot);

        /** Sets the stable task ID. */
        Builder taskId(String taskId);

        /** Sets the current task state. */
        Builder status(TaskState status);

        /** Sets an optional human-readable state description. */
        Builder statusMessage(@Nullable String statusMessage);

        /** Sets the task creation timestamp. */
        Builder createdAt(Instant createdAt);

        /** Sets the latest state observation timestamp. */
        Builder lastUpdatedAt(Instant lastUpdatedAt);

        /** Sets the optional task lifetime measured from creation. */
        Builder ttl(@Nullable Duration ttl);

        /** Sets the optional suggested client polling interval. */
        Builder pollInterval(@Nullable Duration pollInterval);

        /** Sets input currently required from the client. */
        Builder pendingInput(@Nullable InputRequestBundle pendingInput);

        /** Sets the terminal result. */
        Builder result(@Nullable TaskResult result);

        /** Sets optional protocol metadata. */
        Builder meta(@Nullable Map<String, ?> meta);

        /** Sets the monotonically increasing projection revision. */
        Builder revision(long revision);

        /** Builds an immutable task snapshot. */
        TaskSnapshot build();
    }
}
