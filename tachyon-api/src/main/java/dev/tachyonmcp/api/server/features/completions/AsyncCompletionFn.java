/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.completions;

import dev.tachyonmcp.api.runtime.InteractionContext;
import java.util.concurrent.CompletionStage;

/** Asynchronously produces candidates for a completion request. */
@FunctionalInterface
public interface AsyncCompletionFn {

    /**
     * Produces completion candidates asynchronously.
     *
     * @param context the interaction context
     * @param request the completion request
     * @return a stage that completes with the completion result
     */
    CompletionStage<? extends CompletionResult> apply(InteractionContext context, CompletionRequest request);
}
