/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.runtime.InteractionContext;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Connects MCP task operations to the system that owns task execution.
 *
 * <p>Methods are synchronous by design. Tachyon invokes them from request-serving virtual threads,
 * so implementations may block while calling an external workflow engine or job store.
 */
@ExperimentalApi
public interface TaskExecutionEngine extends AutoCloseable {

    /** Returns the immutable set of optional MCP task operations supported by this engine. */
    Set<TaskFeature> supportedFeatures();

    /** Starts externally executed work and returns its initial task projection. */
    TaskSnapshot start(InteractionContext context, TaskExecutionRequest request) throws Exception;

    /** Returns the authoritative task snapshot, or {@code null} when the task is unknown. */
    @Nullable
    TaskSnapshot refresh(InteractionContext context, String taskId) throws Exception;

    /** Cancels externally executed work and returns its latest snapshot. */
    TaskSnapshot cancel(InteractionContext context, String taskId) throws Exception;

    /** Submits client input to externally executed work. */
    void submitInput(InteractionContext context, String taskId, TaskInput input) throws Exception;

    /** Releases engine-owned resources. */
    @Override
    default void close() throws Exception {}
}
