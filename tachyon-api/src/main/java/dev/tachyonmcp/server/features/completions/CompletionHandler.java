/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.completions;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.features.HandlerFutures;
import java.util.concurrent.CompletionStage;

/**
 * Handles a {@code completion/complete} request for one prompt argument or resource
 * (template) reference and returns candidate values.
 *
 * <p>{@link #handle} runs on a virtual thread — blocking for I/O is the intended contract.
 * Never use {@code synchronized} or call native methods (pins the carrier thread).
 * Use {@link java.util.concurrent.locks.ReentrantLock} instead. For non-blocking backends,
 * override {@link #handleAsync} (or implement {@link AsyncCompletionHandler}).
 */
@FunctionalInterface
public interface CompletionHandler {

    /**
     * Returns completion candidates for the given argument.
     *
     * @param ctx     the interaction context
     * @param request the completion request
     * @return the completion result with candidate values
     * @throws Exception if completion handling fails
     */
    CompletionResult handle(InteractionContext ctx, CompletionRequest request) throws Exception;

    /**
     * Handles a completion request asynchronously. Default delegates to {@link #handle}.
     * Override to integrate async services.
     *
     * @param ctx     the interaction context
     * @param request the completion request
     * @return a future that completes with the completion result
     */
    default CompletionStage<? extends CompletionResult> handleAsync(InteractionContext ctx, CompletionRequest request) {
        return HandlerFutures.completedOrFailed(() -> handle(ctx, request));
    }
}
