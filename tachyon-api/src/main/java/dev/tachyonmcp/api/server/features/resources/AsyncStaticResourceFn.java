/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.resources;

import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.ResourceContents;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous counterpart to {@link StaticResourceFn}. Unlike {@link StaticResourceFn}, does not
 * throw checked exceptions — failures propagate through the returned {@link CompletionStage}.
 * Adapt via {@code ResourceHandler.ofAsync(AsyncStaticResourceFn)}.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface AsyncStaticResourceFn {

    /**
     * Reads and returns the resource contents for the given URI.
     *
     * @param context the interaction context
     * @param uri     the resource URI to read
     * @return a future that completes with the resource contents
     */
    CompletionStage<? extends ResourceContents> handle(InteractionContext context, String uri);
}
