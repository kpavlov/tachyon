/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */
package dev.tachyonmcp.server.features.completions;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.features.HandlerFutures;
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
