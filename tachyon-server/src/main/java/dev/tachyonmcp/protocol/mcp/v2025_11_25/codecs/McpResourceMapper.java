/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Resource;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceTemplate;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;

final class McpResourceMapper {

    private McpResourceMapper() {}

    static Resource toResource(ResourceDescriptor d) {
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

    static ResourceTemplate toResourceTemplate(ResourceTemplateEntry entry) {
        return new ResourceTemplate(
                entry.uriTemplate(),
                entry.description(),
                entry.mimeType(),
                ContentBlockMappers.toProtocolAnnotations(entry.annotations()),
                null,
                entry.name(),
                entry.title(),
                ContentBlockMappers.toProtocolIcons(entry.icons()));
    }

    static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceContents toProtocolResourceContents(
            ResourceContents domain) {
        return ContentBlockMappers.toProtocolResourceContents(domain);
    }
}
