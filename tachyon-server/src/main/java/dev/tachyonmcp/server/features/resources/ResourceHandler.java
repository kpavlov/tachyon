/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.domain.UriTemplateValue;
import dev.tachyonmcp.server.features.HandlerFutures;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/**
 * Reads static and templated resource contents. {@code uriTemplate} is null for static resources;
 * templated requests include the original URI template text and parsed parameters.
 *
 * <p>{@link #handle} and {@link #handleAsync} run on a server-executor virtual thread. Blocking for I/O
 * is the intended synchronous contract.
 * Never use {@code synchronized} or call native methods (pins the carrier thread).
 * Use {@link java.util.concurrent.locks.ReentrantLock} instead.
 */
@FunctionalInterface
public interface ResourceHandler {

    /** Reads and returns the resource contents. */
    ResourceContents handle(
            InteractionContext context, String uri, Map<String, UriTemplateValue> params, @Nullable String uriTemplate)
            throws Exception;

    /**
     * Reads asynchronously. Default delegates to {@link #handle}. The registry awaits the returned
     * stage on the same server-executor virtual thread.
     * Override to integrate async services.
     */
    default CompletionStage<? extends ResourceContents> handleAsync(
            InteractionContext context,
            String uri,
            Map<String, UriTemplateValue> params,
            @Nullable String uriTemplate) {
        HandlerFutures.assumeVirtualThread();
        return HandlerFutures.completedOrFailed(() -> handle(context, uri, params, uriTemplate));
    }

    /**
     * Adapts a two-arg {@link StaticResourceFn} into a {@link ResourceHandler} for a static,
     * fixed-URI resource — no {@code params}/{@code uriTemplate} to ignore.
     */
    static ResourceHandler of(StaticResourceFn fn) {
        return (context, uri, params, uriTemplate) -> fn.handle(context, uri);
    }

    /**
     * Adapts a two-arg {@link AsyncStaticResourceFn} into an {@link AsyncResourceHandler} for a
     * static, fixed-URI resource — no {@code params}/{@code uriTemplate} to ignore.
     */
    static AsyncResourceHandler ofAsync(AsyncStaticResourceFn fn) {
        return (context, uri, params, uriTemplate) -> fn.handle(context, uri);
    }
}
