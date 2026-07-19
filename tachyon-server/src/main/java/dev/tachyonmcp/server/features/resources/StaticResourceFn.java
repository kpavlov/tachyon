/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ResourceContents;

/**
 * Reads a static, fixed-URI resource. Unlike {@link ResourceHandler}, drops the
 * {@code params}/{@code uriTemplate} parameters that are always empty/{@code null} for a
 * non-templated resource. Adapt via {@link ResourceHandler#of(StaticResourceFn)}.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface StaticResourceFn {

    ResourceContents handle(InteractionContext context, String uri) throws Exception;
}
