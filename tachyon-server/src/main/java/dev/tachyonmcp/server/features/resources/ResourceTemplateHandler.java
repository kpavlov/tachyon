/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ResourceContents;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Reads a templated resource. One handler per URI template. */
@FunctionalInterface
public interface ResourceTemplateHandler {

    /** Reads and returns the resource contents for the given URI and path parameters. */
    ResourceContents read(InteractionContext context, String uri, Map<String, String> params) throws Exception;

    /**
     * Reads asynchronously. Default delegates to {@link #read}.
     * Override to integrate async services.
     */
    default CompletionStage<? extends ResourceContents> readAsync(
            InteractionContext context, String uri, Map<String, String> params) {
        try {
            return CompletableFuture.completedFuture(read(context, uri, params));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
