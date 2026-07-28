/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.prompts;

import dev.tachyonmcp.api.runtime.InteractionContext;
import java.util.concurrent.CompletionStage;

/** Asynchronously handles a prompt request. */
@FunctionalInterface
public interface AsyncPromptFn {

    /**
     * Handles a prompt request asynchronously.
     *
     * @param context the interaction context
     * @param request the prompt request
     * @return a stage that completes with the prompt result
     */
    CompletionStage<? extends PromptResult> apply(InteractionContext context, PromptRequest request);
}
