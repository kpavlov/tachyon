/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.completions;

import dev.tachyonmcp.api.runtime.InteractionContext;

/** Synchronously produces candidates for a completion request. */
@FunctionalInterface
public interface CompletionFn {

    /**
     * Produces completion candidates.
     *
     * @param context the interaction context
     * @param request the completion request
     * @return the completion result
     * @throws Exception if handling fails
     */
    CompletionResult apply(InteractionContext context, CompletionRequest request) throws Exception;
}
