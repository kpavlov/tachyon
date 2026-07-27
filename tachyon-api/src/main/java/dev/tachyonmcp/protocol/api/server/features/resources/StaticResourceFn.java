/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.protocol.api.server.features.resources;

import dev.tachyonmcp.protocol.api.runtime.InteractionContext;
import dev.tachyonmcp.protocol.api.server.domain.ResourceContents;

/**
 * Reads a static, fixed-URI resource. Unlike {@code ResourceHandler}, drops the
 * {@code params}/{@code uriTemplate} parameters that are always empty/{@code null} for a
 * non-templated resource. Adapt via {@code ResourceHandler.of(StaticResourceFn)}.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface StaticResourceFn {

    ResourceContents handle(InteractionContext context, String uri) throws Exception;
}
