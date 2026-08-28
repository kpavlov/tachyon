/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.runtime.InteractionContext;

/** Submits client input to externally executed work for {@code tasks/update}. */
@FunctionalInterface
@ExperimentalApi
public interface TaskUpdateFn {

    /**
     * Submits client input to the authoritative system.
     *
     * @param ctx current MCP interaction
     * @param request task input request
     * @throws TaskNotFoundException when the task is unknown
     * @throws Exception when the input cannot be submitted
     */
    void apply(InteractionContext ctx, TaskUpdateRequest request) throws Exception;
}
