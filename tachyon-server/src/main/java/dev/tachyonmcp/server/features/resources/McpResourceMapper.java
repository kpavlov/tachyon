/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ReadResourceRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Resource;
import dev.tachyonmcp.server.domain.*;
import dev.tachyonmcp.server.domain.Icon;
import java.util.List;

final class McpResourceMapper {

    private McpResourceMapper() {}

    static Resource toResource(ResourceDescriptor d) {
        return new Resource(
                d.uri(),
                d.description(),
                d.mimeType(),
                toProtocolAnnotations(d.annotations()),
                d.size(),
                null,
                d.name(),
                d.title(),
                toProtocolIcons(d.icons()));
    }

    static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Annotations toProtocolAnnotations(Annotations domain) {
        if (domain == null) return null;
        var audience = domain.audience() != null
                ? domain.audience().stream()
                        .map(r -> r == Role.USER
                                ? dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role.USER
                                : dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role.ASSISTANT)
                        .toList()
                : null;
        return new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Annotations(
                audience, domain.priority(), domain.lastModified());
    }

    static List<dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Icon> toProtocolIcons(List<Icon> domain) {
        if (domain == null) return null;
        return domain.stream()
                .map(i -> new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Icon(
                        i.src(), i.mimeType(), i.sizes(), i.theme()))
                .toList();
    }

    static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceContents toProtocolResourceContents(
            ResourceContents domain) {
        if (domain == null) return null;
        return switch (domain) {
            case TextResourceContents t ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TextResourceContents(
                        t.text(), t.uri(), t.mimeType(), null);
            case BlobResourceContents b ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.BlobResourceContents(
                        b.blob(), b.uri(), b.mimeType(), null);
        };
    }

    static ReadResourceRequestParams toProtocolReadResourceRequest(ReadResourceRequest domain) {
        if (domain == null) return null;
        return new ReadResourceRequestParams(null, domain.uri());
    }
}
