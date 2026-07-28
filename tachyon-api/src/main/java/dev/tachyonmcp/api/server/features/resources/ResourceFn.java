/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.resources;

import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.ResourceContents;

/** Synchronously reads static or templated resource contents from a full request. */
@FunctionalInterface
public interface ResourceFn {

    /**
     * Reads the requested resource.
     *
     * @param context the interaction context
     * @param request the full resource request
     * @return the resource contents
     * @throws Exception if reading fails
     */
    ResourceContents apply(InteractionContext context, ResourceRequest request) throws Exception;
}
