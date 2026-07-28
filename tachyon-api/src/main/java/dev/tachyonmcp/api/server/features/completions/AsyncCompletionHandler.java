/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.completions;

import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.features.HandlerFutures;
import java.util.concurrent.CompletionStage;

/**
 * Convenient base for asynchronous (non-blocking) completion handlers.
 */
public interface AsyncCompletionHandler extends CompletionHandler {

    /**
     * Handles the completion request asynchronously.
     */
    CompletionStage<? extends CompletionResult> handleAsync(InteractionContext ctx, CompletionRequest request);

    @Override
    default CompletionResult handle(InteractionContext ctx, CompletionRequest request) throws Exception {
        return HandlerFutures.joinInterruptibly(handleAsync(ctx, request));
    }
}
