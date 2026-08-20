/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.tasks;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Re-invokes whatever produced a task's {@link dev.tachyonmcp.api.server.features.tasks.TaskState#INPUT_REQUIRED}
 * pause with this round's merged, filtered input responses and the pause's opaque request state.
 * Fire-and-forget — the implementation drives the task to its next outcome (completed, failed, or
 * parked again) itself; nothing composes on a return value, so there isn't one.
 */
@FunctionalInterface
public interface TaskResumer {

    void resume(Map<String, Object> inputResponses, @Nullable String requestState);
}
