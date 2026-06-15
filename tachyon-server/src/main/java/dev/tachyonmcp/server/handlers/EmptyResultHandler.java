/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.EmptyResult;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.session.McpContext;

public class EmptyResultHandler implements McpMethodHandler {

    private static final EmptyResult EMPTY_RESULT = new EmptyResult(null, null);

    private final String methodName;

    public EmptyResultHandler(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public String method() {
        return methodName;
    }

    @Override
    public Object handle(McpContext context, Object params) {
        return EMPTY_RESULT;
    }
}
