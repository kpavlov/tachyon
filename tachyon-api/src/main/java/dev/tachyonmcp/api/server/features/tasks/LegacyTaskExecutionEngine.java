/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.features.PaginatedResult;
import org.jspecify.annotations.Nullable;

/** Optional connector operations used only by MCP versions with task listing and result waiting. */
@ExperimentalApi
public interface LegacyTaskExecutionEngine extends TaskExecutionEngine {

    /** Lists authoritative task projections. */
    PaginatedResult<TaskSnapshot> list(InteractionContext context, int limit, @Nullable String cursor) throws Exception;

    /** Waits through the external system until an authoritative terminal projection is available. */
    TaskSnapshot awaitResult(InteractionContext context, String taskId) throws Exception;
}
