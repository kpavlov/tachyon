/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.session.McpContext;

@FunctionalInterface
public interface ResourceHandler {

    ResourceContents read(McpContext context, ReadResourceRequest request);
}
