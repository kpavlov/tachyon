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

    /** Returns the optional terminal-result retention duration. */
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

    /** Validates task identity and revision. */
    @Value.Check
    default void check() {
        if (taskId().isBlank()) {
            throw new IllegalArgumentException("taskId cannot be blank");
        }
        if (revision() < 0) {
            throw new IllegalArgumentException("revision cannot be negative: " + revision());
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

        /** Sets the optional terminal-result retention duration. */
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
