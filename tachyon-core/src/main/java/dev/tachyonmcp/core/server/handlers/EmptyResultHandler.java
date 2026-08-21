/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.RpcMethodHandler.NoParams;
import dev.tachyonmcp.core.server.session.DispatchContext;
import org.jspecify.annotations.Nullable;

@InternalApi
public class EmptyResultHandler implements RpcMethodHandler<NoParams, Object> {

    private final String methodName;

    public EmptyResultHandler(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public String method() {
        return methodName;
    }

    @Override
    public NoParams decode(DispatchContext context, @Nullable Object rawParams) {
        return NoParams.INSTANCE;
    }

    @Override
    public Object handle(DispatchContext context, NoParams params) {
        return context.responseMapper().emptyResult();
    }
}
