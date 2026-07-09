/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.domain.ResourceContents;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Reads a resource's contents. One handler per resource.
 *
 * <p>{@link #read} runs on a virtual thread — blocking for I/O is the intended contract.
 * Never use {@code synchronized} or call native methods (pins the carrier thread).
 * Use {@link java.util.concurrent.locks.ReentrantLock} instead.
 */
@FunctionalInterface
public interface ResourceHandler {

    /** Reads and returns the resource contents. */
    ResourceContents read(InteractionContext context, ReadResourceRequest request) throws Exception;

    /**
     * Reads asynchronously. Default delegates to {@link #read}.
     * Override to integrate async services.
     */
    default CompletionStage<? extends ResourceContents> readAsync(
            InteractionContext context, ReadResourceRequest request) {
        try {
            return CompletableFuture.completedFuture(read(context, request));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
