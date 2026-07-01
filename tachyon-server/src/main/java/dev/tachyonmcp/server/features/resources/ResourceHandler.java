/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.session.McpContext;

/** Reads a resource's contents. One handler per resource. */
@FunctionalInterface
public interface ResourceHandler {

    /** Reads and returns the resource contents. */
    ResourceContents read(McpContext context, ReadResourceRequest request);
}
