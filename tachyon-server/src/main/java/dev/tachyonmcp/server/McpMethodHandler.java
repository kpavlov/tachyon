/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.session.McpContext;

/** Handles a single JSON-RPC method. */
public interface McpMethodHandler {

    /** The JSON-RPC method name this handler dispatches to. */
    String method();

    /** Handles the method and returns the result to serialize as JSON-RPC response. */
    Object handle(McpContext context, Object params) throws Exception;
}
