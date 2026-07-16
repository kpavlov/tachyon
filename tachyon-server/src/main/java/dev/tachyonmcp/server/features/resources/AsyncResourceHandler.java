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
 * Convenient base for asynchronous resource handlers. The registry invokes {@link #readAsync} and
 * awaits its result on a server-executor virtual thread.
 */
public interface AsyncResourceHandler extends ResourceHandler {

    /**
     * Reads the resource asynchronously and returns a future of its contents.
     */
    CompletionStage<? extends ResourceContents> readAsync(
            InteractionContext context, String uri, Map<String, UriTemplateValue> params, @Nullable String uriTemplate);

    @Override
    default ResourceContents read(
            InteractionContext context, String uri, Map<String, UriTemplateValue> params, @Nullable String uriTemplate)
            throws Exception {
        HandlerFutures.assumeVirtualThread();
        return HandlerFutures.joinInterruptibly(readAsync(context, uri, params, uriTemplate));
    }
}
