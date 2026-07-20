/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.completions;

import dev.tachyonmcp.runtime.InteractionContext;
import java.util.concurrent.CompletableFuture;
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
     */
    CompletionResult handle(InteractionContext ctx, CompletionRequest request) throws Exception;

    /**
     * Handles a completion request asynchronously. Default delegates to {@link #handle}.
     * Override to integrate async services.
     */
    default CompletionStage<? extends CompletionResult> handleAsync(InteractionContext ctx, CompletionRequest request) {
        try {
            return CompletableFuture.completedFuture(handle(ctx, request));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
