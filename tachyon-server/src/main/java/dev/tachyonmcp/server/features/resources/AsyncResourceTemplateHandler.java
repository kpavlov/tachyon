/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.features.HandlerFutures;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Convenient base for asynchronous (non-blocking) resource template handlers.
 */
public interface AsyncResourceTemplateHandler extends ResourceTemplateHandler {

    /**
     * Reads the resource asynchronously and returns a future of its contents.
     */
    CompletionStage<? extends ResourceContents> readAsync(
            InteractionContext context, String uri, Map<String, String> params);

    @Override
    default ResourceContents read(InteractionContext context, String uri, Map<String, String> params) throws Exception {
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
