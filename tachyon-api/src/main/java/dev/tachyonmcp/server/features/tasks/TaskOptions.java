/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.server.domain.HasMeta;
import java.time.Duration;
import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface TaskOptions extends HasMeta {

    /** Caller-supplied task ID to correlate with an external task runner, or {@code null} to auto-generate. */
    @Nullable
    String id();

    @Nullable
    Duration ttl();

    /** How long after this task reaches a terminal state its result stays retrievable, or {@code null} to use the server default. */
    @Nullable
    Duration keepAlive();

    /** Suggested {@code tasks/get} polling interval to advertise, or {@code null} to use the server default. */
    @Nullable
    Duration pollInterval();

    @Nullable
    @Override
    Map<String, Object> meta();

    @Value.Check
    default void check() {
        final var id = id();
        if (id != null && id.isBlank()) {
            throw new IllegalArgumentException("Provided task id must not be blank");
        }
    }

    static Builder builder() {
        return DefaultTaskOptions.builder();
    }

    interface Builder {
        Builder id(@Nullable String id);

        Builder ttl(@Nullable Duration ttl);

        Builder keepAlive(@Nullable Duration keepAlive);

        Builder pollInterval(@Nullable Duration pollInterval);

        Builder meta(@Nullable Map<String, ?> meta);

        TaskOptions build();
    }
}
