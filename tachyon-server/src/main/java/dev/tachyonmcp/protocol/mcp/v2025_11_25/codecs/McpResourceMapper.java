/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Resource;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;

public final class McpResourceMapper {

    private McpResourceMapper() {}

    public static Resource toResource(ResourceDescriptor d) {
        return new Resource(
                d.uri(),
                d.description(),
                d.mimeType(),
                ContentBlockMappers.toProtocolAnnotations(d.annotations()),
                d.size(),
                null,
                d.name(),
                d.title(),
                ContentBlockMappers.toProtocolIcons(d.icons()));
    }

    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceContents toProtocolResourceContents(
            ResourceContents domain) {
        return ContentBlockMappers.toProtocolResourceContents(domain);
    }
}
