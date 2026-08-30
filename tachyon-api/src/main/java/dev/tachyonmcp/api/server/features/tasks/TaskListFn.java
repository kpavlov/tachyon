/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.annotations.LegacyApi;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.features.PaginatedResult;

/** Lists authoritative task projections for the legacy (pre-SEP-2663) {@code tasks/list}. */
@FunctionalInterface
@ExperimentalApi
@LegacyApi
public interface TaskListFn {

    /**
     * Lists authoritative task projections.
     *
     * @param ctx current MCP interaction
     * @param request resolved legacy list request
     * @return requested page of task snapshots
     * @throws Exception when the authoritative system cannot serve the list
     */
    PaginatedResult<TaskSnapshot> apply(InteractionContext ctx, TaskListRequest request) throws Exception;
}
