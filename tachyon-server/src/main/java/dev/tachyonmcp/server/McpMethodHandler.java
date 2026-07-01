/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.session.McpContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Handles a single JSON-RPC method. */
public interface McpMethodHandler {

    /** The JSON-RPC method name this handler dispatches to. */
    String method();

    /** Handles the method and returns the result to serialize as JSON-RPC response. */
    Object handle(McpContext context, Object params) throws Exception;

    /**
     * Handles the method asynchronously. Default delegates to {@link #handle}.
     * Override for async handlers that return a future directly.
     */
    default CompletionStage<Object> handleAsync(McpContext context, Object params) throws Exception {
        return CompletableFuture.completedFuture(handle(context, params));
    }
}
