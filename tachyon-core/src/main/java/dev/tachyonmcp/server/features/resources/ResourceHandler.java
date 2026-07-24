/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.features.HandlerFutures;
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

    /** Reads and returns the resource contents. */
    ResourceContents handle(InteractionContext context, ResourceRequest request) throws Exception;

    /**
     * Reads asynchronously. Default delegates to {@link #handle}. The registry awaits the returned
     * stage on the same server-executor virtual thread.
     * Override to integrate async services.
     */
    default CompletionStage<? extends ResourceContents> handleAsync(
            InteractionContext context, ResourceRequest request) {
        HandlerFutures.assumeVirtualThread();
        return HandlerFutures.completedOrFailed(() -> handle(context, request));
    }

    /**
     * Adapts a two-arg {@link StaticResourceFn} into a {@link ResourceHandler} for a static,
     * fixed-URI resource — no {@code params}/{@code uriTemplate} to ignore.
     */
    static ResourceHandler of(StaticResourceFn fn) {
        return (context, request) -> fn.handle(context, request.uri());
    }

    /**
     * Adapts a two-arg {@link AsyncStaticResourceFn} into an {@link AsyncResourceHandler} for a
     * static, fixed-URI resource — no {@code params}/{@code uriTemplate} to ignore.
     */
    static AsyncResourceHandler ofAsync(AsyncStaticResourceFn fn) {
        return (context, request) -> fn.handle(context, request.uri());
    }
}
