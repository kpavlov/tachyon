/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.runtime.InteractionContext;

/**
 * Requests cancellation of externally executed work for {@code tasks/cancel}.
 *
 * <p>Cancellation is cooperative and may settle after {@link #apply} returns. Tachyon acknowledges
 * the request immediately; the caller observes the authoritative outcome through a later {@code
 * tasks/get}.
 */
@FunctionalInterface
@ExperimentalApi
public interface TaskCancelFn {

    /**
     * Signals cancellation intent to the authoritative system.
     *
     * @param ctx current MCP interaction
     * @param request task cancellation request
     * @throws TaskNotFoundException when the task is unknown
     * @throws Exception when cancellation cannot be requested
     */
    void apply(InteractionContext ctx, TaskCancelRequest request) throws Exception;
}
