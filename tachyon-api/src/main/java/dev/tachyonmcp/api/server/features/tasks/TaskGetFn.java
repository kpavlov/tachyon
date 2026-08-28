/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.runtime.InteractionContext;

/** Returns the authoritative task snapshot for {@code tasks/get}. */
@FunctionalInterface
@ExperimentalApi
public interface TaskGetFn {

    /**
     * Retrieves one task from the authoritative system.
     *
     * @param ctx current MCP interaction
     * @param request task lookup request
     * @return current task snapshot
     * @throws TaskNotFoundException when the task is unknown
     * @throws Exception when the authoritative system cannot serve the lookup
     */
    TaskSnapshot apply(InteractionContext ctx, TaskGetRequest request) throws Exception;
}
