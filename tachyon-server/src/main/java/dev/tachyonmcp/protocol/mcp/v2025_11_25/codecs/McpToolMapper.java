/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Icon;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Tool;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ToolExecution;
import dev.tachyonmcp.server.domain.*;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

final class McpToolMapper {

    private static final ObjectNode DEFAULT_SCHEMA =
            JsonNodeFactory.instance.objectNode().put("type", "object");

    private McpToolMapper() {}

    public static ToolResult toDomainResult(Object result) {
        if (result instanceof ToolResult r) return r;
        var text = TextContent.of(result != null ? result.toString() : "");
        return ToolResult.blocks(text);
    }

    public static Tool toTool(ToolDescriptor d) {
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

    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ToolAnnotations toProtocolToolAnnotations(
            @Nullable ToolAnnotations domain) {
        if (domain == null) return null;
        return new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ToolAnnotations(
                domain.title(),
                domain.readOnlyHint(),
                domain.destructiveHint(),
                domain.idempotentHint(),
                domain.openWorldHint());
    }

    @Nullable
    public static List<dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Icon> toProtocolIcons(
            @Nullable List<? extends dev.tachyonmcp.server.domain.Icon> domain) {
        return ContentBlockMappers.toProtocolIcons(domain);
    }

    @Nullable
    public static List<dev.tachyonmcp.server.domain.Icon> toDomainIcons(@Nullable List<Icon> protocol) {
        if (protocol == null) return null;
        return protocol.stream()
                .map(i -> dev.tachyonmcp.server.domain.Icon.of(i.src(), i.mimeType(), i.sizes(), i.theme()))
                .toList();
    }

    public static ContentBlock toDomainContentBlock(
            dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ContentBlock protocol) {
        if (protocol == null) return null;
        return switch (protocol) {
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TextContent t ->
                TextContent.of(t.text(), t._meta(), toDomainAnnotations(t.annotations()));
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ImageContent i ->
                ImageContent.of(i.data(), i.mimeType(), toDomainAnnotations(i.annotations()), i._meta());
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.AudioContent a ->
                AudioContent.of(a.data(), a.mimeType(), toDomainAnnotations(a.annotations()), a._meta());
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceLink r ->
                ResourceLink.builder(r.uri(), r.name())
                        .title(r.title())
                        .icons(toDomainIcons(r.icons()))
                        .description(r.description())
                        .mimeType(r.mimeType())
                        .annotations(toDomainAnnotations(r.annotations()))
                        .size(r.size())
                        .meta(r._meta())
                        .build();
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.EmbeddedResource e ->
                EmbeddedResource.of(
                        toDomainResourceContents(e.resource()), toDomainAnnotations(e.annotations()), e._meta());
        };
    }

    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ContentBlock toProtocolContentBlock(
            ContentBlock domain) {
        return ContentBlockMappers.toProtocolContentBlock(domain);
    }

    public static Annotations toDomainAnnotations(dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Annotations protocol) {
        if (protocol == null) return null;
        var audience = protocol.audience() != null
                ? protocol.audience().stream()
                        .map(r -> r == dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Role.USER
                                ? Role.USER
                                : Role.ASSISTANT)
                        .toList()
                : null;
        return Annotations.of(audience, protocol.priority(), protocol.lastModified());
    }

    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Annotations toProtocolAnnotations(Annotations domain) {
        return ContentBlockMappers.toProtocolAnnotations(domain);
    }

    public static ResourceContents toDomainResourceContents(
            dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceContents protocol) {
        if (protocol == null) return null;
        return switch (protocol) {
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TextResourceContents t ->
                TextResourceContents.of(t.uri(), t.mimeType(), t.text(), t._meta());
            case dev.tachyonmcp.protocol.mcp.v2025_11_25.models.BlobResourceContents b ->
                BlobResourceContents.of(b.uri(), b.mimeType(), b.blob(), b._meta());
        };
    }

    public static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ResourceContents toProtocolResourceContents(
            ResourceContents domain) {
        return ContentBlockMappers.toProtocolResourceContents(domain);
    }
}
