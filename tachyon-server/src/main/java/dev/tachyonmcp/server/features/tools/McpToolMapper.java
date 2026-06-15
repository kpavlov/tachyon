/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Icon;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Tool;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ToolExecution;
import dev.tachyonmcp.server.domain.*;
import dev.tachyonmcp.server.domain.EmbeddedResource;
import dev.tachyonmcp.server.domain.ImageContent;
import dev.tachyonmcp.server.domain.ResourceLink;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.domain.ToolAnnotations;
import java.util.List;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

final class McpToolMapper {

    private static final ObjectNode DEFAULT_SCHEMA =
            JsonNodeFactory.instance.objectNode().put("type", "object");

    private McpToolMapper() {}

    static ToolResult toDomainResult(Object result) {
        if (result instanceof ToolResult r) return r;
        var text = new TextContent(result != null ? result.toString() : "", null);
        return ToolResult.of(List.of(text));
    }

    static Tool toTool(ToolDescriptor d) {
        var schema = d.inputSchema();
        if (schema == null) {
            schema = DEFAULT_SCHEMA;
        }
        ToolExecution execution = null;
        if (d.taskSupport() != null) {
            execution = new ToolExecution(d.taskSupport().getValue());
        }
        return new Tool(
                d.description(),
                schema,
                execution,
                d.outputSchema(),
                toProtocolToolAnnotations(d.annotations()),
                null,
                d.name(),
                d.title(),
                toProtocolIcons(d.icons()));
    }

    static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ToolAnnotations toProtocolToolAnnotations(
            ToolAnnotations domain) {
        if (domain == null) return null;
        return new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ToolAnnotations(
                null, domain.readOnlyHint(), domain.destructiveHint(), domain.idempotentHint(), domain.openWorldHint());
    }

    static List<Icon> toProtocolIcons(List<dev.tachyonmcp.server.domain.Icon> domain) {
        if (domain == null) return null;
        return domain.stream()
                .map(i -> new Icon(i.src(), i.mimeType(), i.sizes(), i.theme()))
                .toList();
    }

    static ContentBlock toDomainContentBlock(dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ContentBlock protocol) {
        if (protocol == null) return null;
        return switch (protocol) {
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TextContent t ->
                new TextContent(t.text(), toDomainAnnotations(t.annotations()));
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ImageContent i ->
                new ImageContent(i.data(), i.mimeType(), toDomainAnnotations(i.annotations()));
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.AudioContent a ->
                new AudioContent(a.data(), a.mimeType(), toDomainAnnotations(a.annotations()));
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceLink r ->
                new ResourceLink(
                        r.uri(),
                        r.name(),
                        r.title(),
                        r.description(),
                        r.mimeType(),
                        toDomainAnnotations(r.annotations()));
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.EmbeddedResource e ->
                new EmbeddedResource(toDomainResourceContents(e.resource()), toDomainAnnotations(e.annotations()));
            default -> throw new IllegalArgumentException("Unknown content block type: " + protocol);
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

    static Annotations toDomainAnnotations(dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Annotations protocol) {
        if (protocol == null) return null;
        var audience = protocol.audience() != null
                ? protocol.audience().stream()
                        .map(r -> r == dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role.USER
                                ? Role.USER
                                : Role.ASSISTANT)
                        .toList()
                : null;
        return new Annotations(audience, protocol.priority(), protocol.lastModified());
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

    static ResourceContents toDomainResourceContents(
            dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceContents protocol) {
        if (protocol == null) return null;
        return switch (protocol) {
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TextResourceContents t ->
                new TextResourceContents(t.uri(), t.mimeType(), t.text());
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.BlobResourceContents b ->
                new BlobResourceContents(b.uri(), b.mimeType(), b.blob());
        };
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
