/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Handles tool execution. One handler per tool.
 *
 * <p>{@link #handle} runs on a virtual thread — blocking for I/O is the intended contract.
 * Never use {@code synchronized} or call native methods (pins the carrier thread).
 * Use {@link java.util.concurrent.locks.ReentrantLock} instead. For CPU-bound work or
 * third-party code that may synchronize, offload to
 * {@code context.server().executor()}.
 */
public interface ToolHandler {

    /**
     * Returns the metadata descriptor for this tool.
     */
    ToolDescriptor descriptor();

    /**
     * Executes the tool with the given context and request.
     */
    ToolResult handle(InteractionContext context, ToolRequest request) throws Exception;

    /**
     * Executes the tool asynchronously. Default delegates to {@link #handle}.
     * Override only to integrate legacy async services.
     */
    default CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, ToolRequest request) {
        try {
            return CompletableFuture.completedFuture(handle(context, request));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
