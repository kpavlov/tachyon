/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.protocol.api.server.domain.*;
import dev.tachyonmcp.protocol.api.server.domain.ImageContent;
import dev.tachyonmcp.protocol.api.server.domain.TextContent;
import dev.tachyonmcp.protocol.api.server.domain.TextResourceContents;
import dev.tachyonmcp.server.json.JsonUtils;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps content-block domain types to MCP 2025-11-25 wire models. */
public final class ContentBlockMappers {

    private ContentBlockMappers() {}

    /**
     * Maps domain annotations to the protocol shape.
     *
     * @param domain the domain annotations, or {@code null}
     * @return the protocol annotations, or {@code null} if {@code domain} is {@code null}
     */
    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Annotations toProtocolAnnotations(Annotations domain) {
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

    /**
     * Maps domain icons to the protocol shape.
     *
     * @param domain the domain icons, or {@code null}
     * @return the protocol icons, or {@code null} if {@code domain} is {@code null}
     */
    @Nullable
    public static List<dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Icon> toProtocolIcons(
            @Nullable List<? extends Icon> domain) {
        if (domain == null) return null;
        return domain.stream()
                .map(i -> new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Icon(
                        i.src(), i.mimeType(), i.sizes(), i.theme()))
                .toList();
    }

    /**
     * Maps a domain content block to the protocol shape.
     *
     * @param domain the domain content block, or {@code null}
     * @return the protocol content block, or {@code null} if {@code domain} is {@code null}
     */
    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ContentBlock toProtocolContentBlock(
            ContentBlock domain) {
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

    /**
     * Maps domain resource contents to the protocol shape.
     *
     * @param domain the domain resource contents, or {@code null}
     * @return the protocol resource contents, or {@code null} if {@code domain} is {@code null}
     */
    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceContents toProtocolResourceContents(
            ResourceContents domain) {
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
