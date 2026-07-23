/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.features.HandlerFutures;
import java.util.concurrent.CompletionStage;

/**
 * Convenient base for asynchronous resource handlers. The registry invokes {@link #handleAsync} and
 * awaits its result on a server-executor virtual thread.
 */
public interface AsyncResourceHandler extends ResourceHandler {

    /**
     * Reads the resource asynchronously and returns a future of its contents.
     */
    CompletionStage<? extends ResourceContents> handleAsync(InteractionContext context, ResourceRequest request);

    @Override
    default ResourceContents handle(InteractionContext context, ResourceRequest request) throws Exception {
        HandlerFutures.assumeVirtualThread();
        return HandlerFutures.joinInterruptibly(handleAsync(context, request));
    }
}
