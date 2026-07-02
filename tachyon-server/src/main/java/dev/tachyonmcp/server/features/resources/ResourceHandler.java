/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.domain.ResourceContents;

/** Reads a resource's contents. One handler per resource. */
@FunctionalInterface
public interface ResourceHandler {

    /** Reads and returns the resource contents. */
    ResourceContents read(InteractionContext context, ReadResourceRequest request);
}
