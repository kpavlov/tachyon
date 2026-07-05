/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Handles tool execution. One handler per tool.
 */
public interface ToolHandler {

    /**
     * Returns the metadata descriptor for this tool.
     */
    ToolDescriptor descriptor();

    /**
     * Executes the tool with the given context and request.
     * Runs on the server executor which must be thread-per-task (e.g. virtual threads).
     * Blocking is expected and fine; bounded pools deadlock with this contract.
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
