/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.runtime.InteractionContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Handler for prompts that may require additional input from the client (MRTR).
 */
@FunctionalInterface
public interface InputRequiredPromptHandler {

    /**
     * Handles a prompt request and returns a result (messages or input-required).
     */
    PromptHandlerResult handle(InteractionContext ctx, PromptRequest request) throws Exception;

    /**
     * Handles a prompt request asynchronously. Default delegates to {@link #handle}.
     * Override to integrate async services.
     */
    default CompletionStage<? extends PromptHandlerResult> handleAsync(InteractionContext ctx, PromptRequest request) {
        try {
            return CompletableFuture.completedFuture(handle(ctx, request));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
