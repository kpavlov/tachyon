/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.session.DispatchContext;

public class EmptyResultHandler implements RpcMethodHandler {

    private final String methodName;

    public EmptyResultHandler(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public String method() {
        return methodName;
    }

    @Override
    public Object handle(DispatchContext context, Object params) {
        return context.responseMapper().emptyResult();
    }
}
