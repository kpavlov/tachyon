/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ResourceContents;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous counterpart to {@link StaticResourceFn}. Unlike {@link StaticResourceFn}, does not
 * throw checked exceptions — failures propagate through the returned {@link CompletionStage}.
 * Adapt via {@link ResourceHandler#ofAsync(AsyncStaticResourceFn)}.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface AsyncStaticResourceFn {

    CompletionStage<? extends ResourceContents> handle(InteractionContext context, String uri);
}
