/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.session.McpContext;

public class EmptyResultHandler implements McpMethodHandler {

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
        return context.responseMapper().emptyResult();
    }
}
