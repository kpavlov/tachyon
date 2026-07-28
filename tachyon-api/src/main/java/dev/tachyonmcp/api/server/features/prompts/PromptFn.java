/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.prompts;

import dev.tachyonmcp.api.runtime.InteractionContext;

/** Synchronously handles a prompt request. */
@FunctionalInterface
public interface PromptFn {

    /**
     * Handles a prompt request.
     *
     * @param context the interaction context
     * @param request the prompt request
     * @return the prompt result
     * @throws Exception if handling fails
     */
    PromptResult apply(InteractionContext context, PromptRequest request) throws Exception;
}
