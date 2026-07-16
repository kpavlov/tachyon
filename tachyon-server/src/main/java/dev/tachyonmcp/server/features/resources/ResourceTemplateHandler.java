/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.domain.UriTemplateValue;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Reads a templated resource. One handler per URI template. */
@FunctionalInterface
public interface ResourceTemplateHandler {

    /**
     * Reads the resource identified by the URI using the supplied template variables.
     *
     * @param uri    the resource URI
     * @param params the URI template variables
     * @return the resource contents
     * @throws Exception if the resource cannot be read
     */
    ResourceContents read(InteractionContext context, String uri, Map<String, UriTemplateValue> params)
            throws Exception;

    /**
     * Reads the resource identified by the URI and template variables.
     *
     * @param  params template variables used to resolve the resource URI
     * @return       the resource contents, or a failed stage if reading fails
     */
    default CompletionStage<? extends ResourceContents> readAsync(
            InteractionContext context, String uri, Map<String, UriTemplateValue> params) {
        try {
            return CompletableFuture.completedFuture(read(context, uri, params));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
