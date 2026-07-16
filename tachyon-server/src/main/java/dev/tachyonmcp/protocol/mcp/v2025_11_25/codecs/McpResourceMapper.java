/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Resource;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceTemplate;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor;

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

    static ResourceTemplate toResourceTemplate(ResourceTemplateDescriptor descriptor) {
        return new ResourceTemplate(
                descriptor.uriTemplate(),
                descriptor.description(),
                descriptor.mimeType(),
                ContentBlockMappers.toProtocolAnnotations(descriptor.annotations()),
                null,
                descriptor.name(),
                descriptor.title(),
                ContentBlockMappers.toProtocolIcons(descriptor.icons()));
    }

    static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceContents toProtocolResourceContents(
            ResourceContents domain) {
        return ContentBlockMappers.toProtocolResourceContents(domain);
    }
}
