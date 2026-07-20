/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import java.time.Duration;
import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface TaskOptions {

    /** Caller-supplied task ID to correlate with an external task runner, or {@code null} to auto-generate. */
    @Nullable
    String id();

    @Nullable
    Duration ttl();

    @Nullable
    Map<String, JsonNode> meta();

    @Value.Check
    default void check() {
        if (id() != null && id().isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }

    static Builder builder() {
        return DefaultTaskOptions.builder();
    }

    interface Builder {
        Builder id(@Nullable String id);

        Builder ttl(@Nullable Duration ttl);

        Builder meta(@Nullable Map<String, ? extends JsonNode> meta);

        TaskOptions build();
    }
}
