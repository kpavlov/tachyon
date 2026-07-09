/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.features.HandlerFutures;
import dev.tachyonmcp.server.session.DispatchContext;
import java.util.concurrent.CompletionStage;

/**
 * Convenient base for asynchronous RPC method handlers.
 * Subclasses implement {@link #handleAsync}; the blocking {@link #handle} delegates via
 * {@link HandlerFutures#joinInterruptibly}.
 */
public abstract class AbstractAsyncMethodHandler implements RpcMethodHandler {

    @Override
    public final Object handle(DispatchContext context, Object params) throws Exception {
        return HandlerFutures.joinInterruptibly(handleAsync(context, params));
    }

    @Override
    public abstract CompletionStage<Object> handleAsync(DispatchContext context, Object params);
}
