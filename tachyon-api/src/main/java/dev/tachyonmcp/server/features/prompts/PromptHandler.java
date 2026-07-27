/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.features.HandlerFutures;
import java.util.concurrent.CompletionStage;

/**
 * Handles a prompt request and returns a {@link PromptResult} — either messages or an
 * input-required (MRTR) round-trip.
 *
 * <p>{@link #handle} runs on a virtual thread — blocking for I/O is the intended contract.
 * Never use {@code synchronized} or call native methods (pins the carrier thread).
 * Use {@link java.util.concurrent.locks.ReentrantLock} instead. For non-blocking backends,
 * override {@link #handleAsync} (or implement {@link AsyncPromptHandler}).
 */
@FunctionalInterface
public interface PromptHandler {

    /**
     * Handles a prompt request and returns a result (messages or input-required).
     *
     * @param ctx     the interaction context
     * @param request the prompt request
     * @return the prompt result containing messages or input-required
     * @throws Exception if prompt handling fails
     */
    PromptResult handle(InteractionContext ctx, PromptRequest request) throws Exception;

    /**
     * Handles a prompt request asynchronously. Default delegates to {@link #handle}.
     * Override to integrate async services.
     *
     * @param ctx     the interaction context
     * @param request the prompt request
     * @return a future that completes with the prompt result
     */
    default CompletionStage<? extends PromptResult> handleAsync(InteractionContext ctx, PromptRequest request) {
        return HandlerFutures.completedOrFailed(() -> handle(ctx, request));
    }
}
