/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.features.HandlerFutures;
import java.util.concurrent.CompletionStage;

/**
 * Convenient base for asynchronous (non-blocking) prompt handlers.
 */
public interface AsyncPromptHandler extends InputRequiredPromptHandler {

    /**
     * Handles the prompt request asynchronously.
     */
    CompletionStage<? extends PromptHandlerResult> handleAsync(InteractionContext ctx, PromptRequest request);

    @Override
    default PromptHandlerResult handle(InteractionContext ctx, PromptRequest request) throws Exception {
        return HandlerFutures.joinInterruptibly(handleAsync(ctx, request));
    }
}
