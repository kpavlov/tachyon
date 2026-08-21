/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.session.DispatchContext;

public class EmptyResultHandler implements RpcMethodHandler<Object, Object> {

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
