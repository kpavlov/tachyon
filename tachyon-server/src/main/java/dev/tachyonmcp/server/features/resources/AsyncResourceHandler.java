/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.features.HandlerFutures;
import java.util.concurrent.CompletionStage;

/**
 * Convenient base for asynchronous (non-blocking) resource handlers.
 */
public interface AsyncResourceHandler extends ResourceHandler {

    /**
     * Reads the resource asynchronously and returns a future of its contents.
     */
    CompletionStage<? extends ResourceContents> readAsync(InteractionContext context, ReadResourceRequest request);

    @Override
    default ResourceContents read(InteractionContext context, ReadResourceRequest request) throws Exception {
        return HandlerFutures.joinInterruptibly(readAsync(context, request));
    }
}
