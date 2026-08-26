/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Re-invokes whatever produced a task's {@link dev.tachyonmcp.api.server.features.tasks.TaskState#INPUT_REQUIRED}
 * pause with this round's merged, filtered input responses and the pause's opaque request state.
 * Fire-and-forget — the implementation drives the task to its next outcome (completed, failed, or
 * parked again) itself; nothing composes on a return value, so there isn't one.
 *
 * <p>This is the dispatch-driven form, carrying the request context needed to re-invoke a tool
 * handler. Server code that owns a task it created itself supplies the public
 * {@link dev.tachyonmcp.api.server.features.tasks.TaskResumer} instead.
 */
@InternalApi
@FunctionalInterface
public interface TaskDispatchResumer {

    /**
     * Resumes a task from the {@code tasks/update} request that supplied its input.
     *
     * @param context the current update request context
     * @param inputResponses merged, filtered responses
     * @param requestState the opaque state from the pending input request
     */
    void resume(DispatchContext context, Map<String, Object> inputResponses, @Nullable String requestState);
}
