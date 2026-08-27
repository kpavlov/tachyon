/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/** Client input submitted to an externally executed task. */
@ExperimentalApi
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE, typeImmutable = "Default*")
public interface TaskInput {

    /** Returns input values keyed by request name. */
    Map<String, Object> inputResponses();

    /** Returns opaque state supplied with the corresponding input request. */
    @Nullable
    String requestState();

    /** Creates a builder for task input. */
    static Builder builder() {
        return DefaultTaskInput.builder();
    }

    /** Builder for {@link TaskInput}. */
    interface Builder {
        /** Copies values from existing task input. */
        Builder from(TaskInput input);

        /** Sets input values keyed by request name. */
        Builder inputResponses(Map<String, ?> inputResponses);

        /** Sets opaque state supplied with the corresponding input request. */
        Builder requestState(@Nullable String requestState);

        /** Builds immutable task input. */
        TaskInput build();
    }
}
