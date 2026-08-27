/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.server.domain.HasMeta;
import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/** Provider-neutral operation envelope used by connectors with an explicit start helper. */
@ExperimentalApi
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE, typeImmutable = "Default*")
public interface TaskExecutionRequest extends HasMeta {

    /** Returns the caller-assigned stable MCP task identifier. */
    String taskId();

    /** Returns the application operation name, such as an MCP tool name. */
    String operation();

    /** Returns provider-neutral JSON arguments. */
    JsonObject arguments();

    /** Returns optional request metadata. */
    @Override
    @Nullable
    Map<String, Object> meta();

    /** Validates required identifiers. */
    @Value.Check
    default void check() {
        if (taskId().isBlank()) {
            throw new IllegalArgumentException("taskId cannot be blank");
        }
        if (operation().isBlank()) {
            throw new IllegalArgumentException("operation cannot be blank");
        }
    }

    /** Creates a builder for an execution request. */
    static Builder builder() {
        return DefaultTaskExecutionRequest.builder();
    }

    /** Builder for {@link TaskExecutionRequest}. */
    interface Builder {
        /** Copies values from an existing execution request. */
        Builder from(TaskExecutionRequest request);

        /** Sets the caller-assigned stable task ID. */
        Builder taskId(String taskId);

        /** Sets the application operation name. */
        Builder operation(String operation);

        /** Sets provider-neutral JSON arguments. */
        Builder arguments(JsonObject arguments);

        /** Sets optional request metadata. */
        Builder meta(@Nullable Map<String, ?> meta);

        /** Builds an immutable execution request. */
        TaskExecutionRequest build();
    }
}
