/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Prompt;
import dev.tachyonmcp.server.domain.*;
import dev.tachyonmcp.server.domain.EmbeddedResource;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.ImageContent;
import dev.tachyonmcp.server.domain.PromptArgument;
import dev.tachyonmcp.server.domain.ResourceLink;
import dev.tachyonmcp.server.domain.TextContent;
import java.util.List;

final class McpPromptMapper {

    private McpPromptMapper() {}

    static Prompt toPrompt(PromptDescriptor d) {
        return new Prompt(
                d.description(),
                toProtocolPromptArguments(d.arguments()),
                null,
                d.name(),
                d.title(),
                toProtocolIcons(d.icons()));
    }

    static List<dev.tachyonmcp.protocol.mcp.v2025_11_25.models.PromptArgument> toProtocolPromptArguments(
            List<PromptArgument> domain) {
        if (domain == null) return null;
        return domain.stream()
                .map(a -> new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.PromptArgument(
                        a.description(), a.required(), a.name(), a.title()))
                .toList();
    }

    static List<dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Icon> toProtocolIcons(List<Icon> domain) {
        if (domain == null) return null;
        return domain.stream()
                .map(i -> new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Icon(
                        i.src(), i.mimeType(), i.sizes(), i.theme()))
                .toList();
    }

    static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.PromptMessage toProtocolMessage(PromptMessage domain) {
        if (domain == null) return null;
        return new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.PromptMessage(
                toProtocolRole(domain.role()), toProtocolContentBlock(domain.content()));
    }

    static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role toProtocolRole(Role domain) {
        if (domain == null) return null;
        return switch (domain) {
            case USER -> dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role.USER;
            case ASSISTANT -> dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role.ASSISTANT;
        };
    }

    static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ContentBlock toProtocolContentBlock(ContentBlock domain) {
        if (domain == null) return null;
        return switch (domain) {
            case TextContent t ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TextContent(
                        "text", t.text(), toProtocolAnnotations(t.annotations()), null);
            case ImageContent i ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ImageContent(
                        "image", i.data(), i.mimeType(), toProtocolAnnotations(i.annotations()), null);
            case AudioContent a ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.AudioContent(
                        "audio", a.data(), a.mimeType(), toProtocolAnnotations(a.annotations()), null);
            case ResourceLink r ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceLink(
                        "resource_link",
                        r.name(),
                        r.title(),
                        null,
                        r.uri(),
                        r.description(),
                        r.mimeType(),
                        toProtocolAnnotations(r.annotations()),
                        null,
                        null);
            case EmbeddedResource e ->
                new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.EmbeddedResource(
                        "resource",
                        toProtocolResourceContents(e.resource()),
                        toProtocolAnnotations(e.annotations()),
                        null);
        };
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
}
