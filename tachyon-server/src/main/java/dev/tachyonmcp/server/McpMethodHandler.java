/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.session.McpContext;

public interface McpMethodHandler {

    String method();

    Object handle(McpContext context, Object params) throws Exception;
}
