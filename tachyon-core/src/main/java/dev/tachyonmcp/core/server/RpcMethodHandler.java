/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.core.server.session.DispatchContext;
import org.jspecify.annotations.Nullable;

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
@InternalApi
public interface RpcMethodHandler<@Nullable I, O> {

    /**
     * The JSON-RPC method name this handler dispatches to.
     *
     * @return the method name
     */
    String method();

    /**
     * Handles the method and returns the result to serialize as JSON-RPC response.
     *
     * @param context the dispatch context with server and outbound stream access
     * @param params  the method parameters, or {@code null}
     * @return the result to serialize
     * @throws Exception on handler failure
     */
    O handle(DispatchContext context, @Nullable I params) throws Exception;

    /**
     * Handles the method asynchronously. Default delegates to {@link #handle}.
     * Override for async handlers that return a future directly.
     *
     * @param context the dispatch context
     * @param params  the method parameters, or {@code null}
     * @return a completion stage yielding the result
     * @throws Exception on handler failure
     */
    default CompletionStage<O> handleAsync(DispatchContext context, @Nullable I params) throws Exception {
        return CompletableFuture.completedFuture(handle(context, params));
    }
}
