/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.domain.UriTemplateValue;
import dev.tachyonmcp.server.features.HandlerFutures;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/**
 * Reads static and templated resource contents. {@code uriTemplate} is null for static resources;
 * templated requests include the original URI template text and parsed parameters.
 *
 * <p>{@link #read} and {@link #readAsync} run on a server-executor virtual thread. Blocking for I/O
 * is the intended synchronous contract.
 * Never use {@code synchronized} or call native methods (pins the carrier thread).
 * Use {@link java.util.concurrent.locks.ReentrantLock} instead.
 */
@FunctionalInterface
public interface ResourceHandler {

    /** Reads and returns the resource contents. */
    ResourceContents read(
            InteractionContext context, String uri, Map<String, UriTemplateValue> params, @Nullable String uriTemplate)
            throws Exception;

    /**
     * Reads asynchronously. Default delegates to {@link #read}. The registry awaits the returned
     * stage on the same server-executor virtual thread.
     * Override to integrate async services.
     */
    default CompletionStage<? extends ResourceContents> readAsync(
            InteractionContext context,
            String uri,
            Map<String, UriTemplateValue> params,
            @Nullable String uriTemplate) {
        HandlerFutures.assumeVirtualThread();
        try {
            return CompletableFuture.completedFuture(read(context, uri, params, uriTemplate));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
