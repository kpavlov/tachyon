/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.runtime.InteractionContext;

/**
 * Waits through the external system until an authoritative terminal projection is available, for
 * the legacy (pre-SEP-2663) blocking {@code tasks/result}.
 *
 * <p>May block in the external client's supported wait operation. Must not be emulated with a
 * local completion future or a polling loop.
 */
@FunctionalInterface
@ExperimentalApi
@Deprecated(forRemoval = false)
public interface TaskAwaitResultFn {

    /**
     * Waits for one authoritative terminal snapshot.
     *
     * @param ctx current MCP interaction
     * @param request legacy result request
     * @return terminal task snapshot
     * @throws TaskNotFoundException when the task is unknown
     * @throws Exception when the authoritative system cannot await the result
     */
    TaskSnapshot apply(InteractionContext ctx, TaskAwaitResultRequest request) throws Exception;
}
