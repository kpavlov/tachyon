/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.resources;

import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.ResourceContents;
import java.util.concurrent.CompletionStage;

/** Asynchronously reads static or templated resource contents from a full request. */
@FunctionalInterface
public interface AsyncResourceFn {

    /**
     * Reads the requested resource asynchronously.
     *
     * @param context the interaction context
     * @param request the full resource request
     * @return a stage that completes with the resource contents
     */
    CompletionStage<? extends ResourceContents> apply(InteractionContext context, ResourceRequest request);
}
