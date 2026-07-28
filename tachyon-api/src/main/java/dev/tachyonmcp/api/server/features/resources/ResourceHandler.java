/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.resources;

import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.ResourceContents;
import dev.tachyonmcp.api.server.features.HandlerFutures;
import java.util.concurrent.CompletionStage;

/**
 * Reads static and templated resource contents.
 *
 * <p>{@link #handle} and {@link #handleAsync} run on a server-executor virtual thread. Blocking for I/O
 * is the intended synchronous contract.
 * Never use {@code synchronized} or call native methods (pins the carrier thread).
 * Use {@link java.util.concurrent.locks.ReentrantLock} instead.
 */
@FunctionalInterface
public interface ResourceHandler {

    /**
     * Reads and returns the resource contents.
     *
     * @param context the interaction context
     * @param request the resource request
     * @return the resource contents
     * @throws Exception if reading fails
     */
    ResourceContents handle(InteractionContext context, ResourceRequest request) throws Exception;

    /**
     * Reads asynchronously. Default delegates to {@link #handle}. The registry awaits the returned
     * stage on the same server-executor virtual thread.
     * Override to integrate async services.
     *
     * @param context the interaction context
     * @param request the resource request
     * @return a future that completes with the resource contents
     */
    default CompletionStage<? extends ResourceContents> handleAsync(
            InteractionContext context, ResourceRequest request) {
        HandlerFutures.assumeVirtualThread();
        return HandlerFutures.completedOrFailed(() -> handle(context, request));
    }

    /**
     * Adapts a two-arg {@link StaticResourceFn} into a {@link ResourceHandler} for a static,
     * fixed-URI resource — no {@code params}/{@code uriTemplate} to ignore.
     *
     * @param fn the static resource function to adapt
     * @return a new resource handler backed by {@code fn}
     */
    static ResourceHandler of(StaticResourceFn fn) {
        return (context, request) -> fn.handle(context, request.uri());
    }

    /**
     * Adapts a two-arg {@link AsyncStaticResourceFn} into an {@link AsyncResourceHandler} for a
     * static, fixed-URI resource — no {@code params}/{@code uriTemplate} to ignore.
     *
     * @param fn the async static resource function to adapt
     * @return a new async resource handler backed by {@code fn}
     */
    static AsyncResourceHandler ofAsync(AsyncStaticResourceFn fn) {
        return (context, request) -> fn.handle(context, request.uri());
    }
}
