/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.server.domain.AudioContent;
import dev.tachyonmcp.server.domain.BlobResourceContents;
import dev.tachyonmcp.server.domain.EmbeddedResource;
import dev.tachyonmcp.server.domain.ImageContent;
import dev.tachyonmcp.server.domain.ResourceLink;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.json.JsonUtils;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class ContentBlockMappers {

    private ContentBlockMappers() {}

    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Annotations toProtocolAnnotations(
            dev.tachyonmcp.server.domain.Annotations domain) {
        if (domain == null) return null;
        var audience = domain.audience() != null
                ? domain.audience().stream()
                        .map(r -> r == dev.tachyonmcp.server.domain.Role.USER
                                ? dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role.USER
                                : dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role.ASSISTANT)
                        .toList()
                : null;
        return new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Annotations(
                audience, domain.priority(), domain.lastModified());
    }

    @Nullable
    public static List<dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Icon> toProtocolIcons(
            @Nullable List<? extends dev.tachyonmcp.server.domain.Icon> domain) {
        if (domain == null) return null;
        return domain.stream()
                .map(i -> new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Icon(
                        i.src(), i.mimeType(), i.sizes(), i.theme()))
                .toList();
    }

    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ContentBlock toProtocolContentBlock(
            dev.tachyonmcp.server.domain.ContentBlock domain) {
        if (domain == null) return null;
        return switch (domain) {
            case TextContent t ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TextContent(
                        t.type().discriminator(),
                        t.text(),
                        toProtocolAnnotations(t.annotations()),
                        JsonUtils.toJsonNodeMap(t.meta()));
            case ImageContent i ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ImageContent(
                        i.type().discriminator(),
                        i.data(),
                        i.mimeType(),
                        toProtocolAnnotations(i.annotations()),
                        JsonUtils.toJsonNodeMap(i.meta()));
            case AudioContent a ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.AudioContent(
                        a.type().discriminator(),
                        a.data(),
                        a.mimeType(),
                        toProtocolAnnotations(a.annotations()),
                        JsonUtils.toJsonNodeMap(a.meta()));
            case ResourceLink r ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceLink(
                        r.type().discriminator(),
                        r.name(),
                        r.title(),
                        toProtocolIcons(r.icons()),
                        r.uri(),
                        r.description(),
                        r.mimeType(),
                        toProtocolAnnotations(r.annotations()),
                        r.size(),
                        JsonUtils.toJsonNodeMap(r.meta()));
            case EmbeddedResource e ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.EmbeddedResource(
                        e.type().discriminator(),
                        toProtocolResourceContents(e.resource()),
                        toProtocolAnnotations(e.annotations()),
                        JsonUtils.toJsonNodeMap(e.meta()));
        };
    }

    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceContents toProtocolResourceContents(
            dev.tachyonmcp.server.domain.ResourceContents domain) {
        if (domain == null) return null;
        return switch (domain) {
            case TextResourceContents t ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TextResourceContents(
                        t.text(), t.uri(), t.mimeType(), JsonUtils.toJsonNodeMap(t.meta()));
            case BlobResourceContents b ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.BlobResourceContents(
                        b.blob(), b.uri(), b.mimeType(), JsonUtils.toJsonNodeMap(b.meta()));
        };
    }
}
