/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.session.DispatchContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Handles a single JSON-RPC method. Internal/advanced SPI — receives the rich
 * {@link DispatchContext} (server, response mapper, outbound stream).
 *
 * <p>{@link #handle} runs on a <b>virtual thread</b> per request. Handler code may block for I/O
 * — that is the intended VT contract — but must <b>never</b> use {@code synchronized}, call native
 * methods, or otherwise pin the carrier thread.
 * Use {@link java.util.concurrent.locks.ReentrantLock} over {@code synchronized} for mutual
 * exclusion. For CPU-bound work or third-party code that may synchronize, offload to
 * {@code context.engine().executor()} and join the result:
 * <pre>{@code
 * var future = CompletableFuture.supplyAsync(
 *         () -> heavyWork(), context.engine().executor());
 * return HandlerFutures.joinInterruptibly(future);
 * }</pre>
 */
public interface RpcMethodHandler {

    /** The JSON-RPC method name this handler dispatches to. */
    String method();

    /** Handles the method and returns the result to serialize as JSON-RPC response. */
    Object handle(DispatchContext context, Object params) throws Exception;

    /**
     * Handles the method asynchronously. Default delegates to {@link #handle}.
     * Override for async handlers that return a future directly.
     */
    default CompletionStage<Object> handleAsync(DispatchContext context, Object params) throws Exception {
        return CompletableFuture.completedFuture(handle(context, params));
    }
}
