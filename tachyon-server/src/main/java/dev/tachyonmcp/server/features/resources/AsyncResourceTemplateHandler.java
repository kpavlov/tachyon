/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.domain.UriTemplateValue;
import dev.tachyonmcp.server.features.HandlerFutures;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Convenient base for asynchronous (non-blocking) resource template handlers.
 */
public interface AsyncResourceTemplateHandler extends ResourceTemplateHandler {

    /**
     * Reads the resource identified by the URI and template parameters.
     *
     * @param params values extracted from the resource template URI
     * @return the resource contents
     */
    CompletionStage<? extends ResourceContents> readAsync(
            InteractionContext context, String uri, Map<String, UriTemplateValue> params);

    /**
     * Reads resource contents synchronously for the specified URI template parameters.
     *
     * @param context the interaction context
     * @param uri the resource URI
     * @param params the URI template parameter values
     * @return the resource contents
     * @throws Exception if the asynchronous read fails or is interrupted
     */
    @Override
    default ResourceContents read(InteractionContext context, String uri, Map<String, UriTemplateValue> params)
            throws Exception {
        return HandlerFutures.joinInterruptibly(readAsync(context, uri, params));
    }

    /**
     * Wraps a synchronous resource template handler as an async one.
     */
    static AsyncResourceTemplateHandler adapt(ResourceTemplateHandler sync) {
        Objects.requireNonNull(sync, "sync");
        return sync::readAsync;
    }
}
