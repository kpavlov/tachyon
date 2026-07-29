/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol.mcp.v2026_07_28.codecs;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.server.config.ServerIdentity;
import dev.tachyonmcp.api.server.domain.Annotations;
import dev.tachyonmcp.api.server.domain.ContentBlock;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import dev.tachyonmcp.api.server.domain.ServerCapabilities;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.domain.ToolAnnotations;
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.McpProtocol;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.BlobResourceContents;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.CallToolResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.CompleteResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.DiscoverResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.EmptyResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.GetPromptResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.Implementation;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ListPromptsResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ListResourceTemplatesResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ListResourcesResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ListToolsResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.Prompt;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.PromptArgument;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ReadResourceResult;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.Resource;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ResourceContents;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ResourceTemplate;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.TextResourceContents;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.Tool;
import dev.tachyonmcp.core.server.json.JsonUtils;
import dev.tachyonmcp.core.transport.jsonrpc.JsonRpcError;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.databind.JsonNode;

/**
 * Maps the modern MCP discovery and empty response shapes.
 */
public final class McpResponseMapper extends dev.tachyonmcp.core.protocol.mcp.v2025_11_25.codecs.McpResponseMapper {

    private static final String COMPLETE = "complete";
    private static final String PUBLIC = "public";

    static {
        register(DiscoverResult.class, new DiscoverResultCodec());
        register(EmptyResult.class, new EmptyResultCodec());
        register(ListToolsResult.class, new ListToolsResultCodec());
        register(ListResourcesResult.class, new ListResourcesResultCodec());
        register(ListResourceTemplatesResult.class, new ListResourceTemplatesResultCodec());
        register(ReadResourceResult.class, new ReadResourceResultCodec());
        register(ListPromptsResult.class, new ListPromptsResultCodec());
        register(CompleteResult.class, new CompleteResultCodec());
        register(CallToolResult.class, new CallToolResultCodec());
        register(GetPromptResult.class, new GetPromptResultCodec());
    }

    @Override
    public boolean supports(String protocolName, String protocolVersion) {
        return "mcp".equalsIgnoreCase(protocolName) && McpProtocol.VERSION.equals(protocolVersion);
    }

    @Override
    public Object emptyResult() {
        return new EmptyResult(null, COMPLETE, null);
    }

    @Override
    public JsonRpcError error(ServerError error) {
        var mapped = super.error(error);
        var code =
                switch (error.kind()) {
                    case RESOURCE_NOT_FOUND -> -32602;
                    case HEADER_MISMATCH -> -32020;
                    case MISSING_REQUIRED_CLIENT_CAPABILITY -> -32021;
                    case UNSUPPORTED_PROTOCOL_VERSION -> -32022;
                    default -> mapped.code();
                };
        var httpStatus =
                switch (error.kind()) {
                    case INVALID_PARAMS,
                            HEADER_MISMATCH,
                            MISSING_REQUIRED_CLIENT_CAPABILITY,
                            UNSUPPORTED_PROTOCOL_VERSION -> 400;
                    case METHOD_NOT_FOUND -> 404;
                    default -> 200;
                };
        return new JsonRpcError(code, mapped.message(), mapped.data(), httpStatus);
    }

    @Override
    public Object discoverResult(
            List<String> supportedVersions, ServerCapabilities capabilities, ServerIdentity serverIdentity) {
        var implementation = ServerInfoMapper.toImplementation(serverIdentity);
        var meta = Map.of("io.modelcontextprotocol/serverInfo", encodeToTree(implementation));
        // The schema models server identity only via the optional
        // _meta["io.modelcontextprotocol/serverInfo"] key (see `meta` above), but the pinned
        // conformance suite still requires a top-level `serverInfo` field too. `Result` permits
        // arbitrary extra keys (`[key: string]: unknown`), so mirror it there via
        // additionalProperties for conformance, in addition to the spec-correct `_meta` location.
        var additionalProperties = Map.of("serverInfo", encodeToTree(implementation));
        return new DiscoverResult(
                supportedVersions,
                ServerInfoMapper.toServerCapabilities(capabilities).build(),
                serverIdentity.instructions(),
                meta,
                COMPLETE,
                0,
                PUBLIC,
                additionalProperties);
    }

    // Caching hints (SEP-2549): fixed ttlMs=0/cacheScope="public" policy, same defaults
    // discoverResult already uses above — no per-primitive caching config surface yet.

    @Override
    public Object completeResult(List<String> values, @Nullable Double total, @Nullable Boolean hasMore) {
        return new CompleteResult(
                new CompleteResult.Completion(List.copyOf(values), total, hasMore), null, COMPLETE, null);
    }

    @Override
    public Object listToolsResult(List<ToolDescriptor> tools, @Nullable String nextCursor) {
        var protocolTools = tools.stream().map(McpResponseMapper::toTool).toList();
        return new ListToolsResult(protocolTools, null, COMPLETE, nextCursor, 0, PUBLIC, null);
    }

    @Override
    public Object listResourcesResult(List<ResourceDescriptor> resources, @Nullable String nextCursor) {
        var protocolResources =
                resources.stream().map(McpResponseMapper::toResource).toList();
        return new ListResourcesResult(protocolResources, null, COMPLETE, nextCursor, 0, PUBLIC, null);
    }

    @Override
    public Object listResourceTemplatesResult(List<ResourceTemplateDescriptor> templates, @Nullable String nextCursor) {
        var protocolTemplates =
                templates.stream().map(McpResponseMapper::toResourceTemplate).toList();
        return new ListResourceTemplatesResult(protocolTemplates, null, COMPLETE, nextCursor, 0, PUBLIC, null);
    }

    @Override
    public Object readResourceResult(List<dev.tachyonmcp.api.server.domain.ResourceContents> contents) {
        var protocolContents =
                contents.stream().map(McpResponseMapper::toResourceContents).toList();
        return new ReadResourceResult(protocolContents, null, COMPLETE, 0, PUBLIC, null);
    }

    @Override
    public Object listPromptsResult(List<PromptDescriptor> prompts, @Nullable String nextCursor) {
        var protocolPrompts = prompts.stream().map(McpResponseMapper::toPrompt).toList();
        return new ListPromptsResult(protocolPrompts, null, COMPLETE, nextCursor, 0, PUBLIC, null);
    }

    @Override
    public Object callToolResult(ToolResult result) {
        Map<String, JsonNode> meta = null;
        ToolResult unwrapped = result;
        if (result instanceof ToolResult.WithMeta(ToolResult inner, Map<String, Object> values)) {
            meta = values.isEmpty() ? null : JsonUtils.toJsonNodeMap(values);
            unwrapped = inner;
        }
        return switch (unwrapped) {
            case ToolResult.InputRequired ignored -> super.callToolResult(result);
            case ToolResult.Error error ->
                buildCallToolResult(List.of(TextContent.of(error.message())), null, true, meta);
            case ToolResult.Success success ->
                buildCallToolResult(success.content(), success.structuredValue(), null, meta);
            case ToolResult.WithMeta ignored -> throw new AssertionError("WithMeta unwrapped above");
            case ToolResult.Deferred ignored ->
                throw new AssertionError("Deferred should not reach callToolResult mapping");
        };
    }

    @Override
    public Object getPromptResult(@Nullable String description, List<PromptMessage> messages) {
        return new GetPromptResult(
                description,
                messages.stream().map(McpResponseMapper::toPromptMessage).toList(),
                null,
                COMPLETE,
                null);
    }

    private static Tool toTool(ToolDescriptor d) {
        return new Tool(
                d.description(),
                d.inputSchema() != null
                        ? d.inputSchema().json()
                        : JsonSchema.objectSchema().json(),
                d.outputSchema() != null ? d.outputSchema().json() : null,
                toToolAnnotations(d.annotations()),
                null,
                d.name(),
                d.title(),
                toIcons(d.icons()));
    }

    private static Resource toResource(ResourceDescriptor d) {
        return new Resource(
                d.uri(),
                d.description(),
                d.mimeType(),
                toAnnotations(d.annotations()),
                d.size(),
                null,
                d.name(),
                d.title(),
                toIcons(d.icons()));
    }

    private static ResourceTemplate toResourceTemplate(ResourceTemplateDescriptor d) {
        return new ResourceTemplate(
                d.uriTemplate(),
                d.description(),
                d.mimeType(),
                toAnnotations(d.annotations()),
                null,
                d.name(),
                d.title(),
                toIcons(d.icons()));
    }

    private static ResourceContents toResourceContents(dev.tachyonmcp.api.server.domain.ResourceContents domain) {
        return switch (domain) {
            case dev.tachyonmcp.api.server.domain.TextResourceContents t ->
                new TextResourceContents(t.text(), t.uri(), t.mimeType(), JsonUtils.toJsonNodeMap(t.meta()));
            case dev.tachyonmcp.api.server.domain.BlobResourceContents b ->
                new BlobResourceContents(b.blob(), b.uri(), b.mimeType(), JsonUtils.toJsonNodeMap(b.meta()));
        };
    }

    private static Prompt toPrompt(PromptDescriptor d) {
        var arguments = d.arguments() == null
                ? null
                : d.arguments().stream()
                        .map(a -> new PromptArgument(a.description(), a.required(), a.name(), a.title()))
                        .toList();
        return new Prompt(d.description(), arguments, null, d.name(), d.title(), toIcons(d.icons()));
    }

    private static CallToolResult buildCallToolResult(
            List<ContentBlock> content,
            @Nullable Object structuredValue,
            @Nullable Boolean isError,
            @Nullable Map<String, JsonNode> meta) {
        var blocks = new ArrayList<>(
                content.stream().map(McpResponseMapper::toContentBlock).toList());
        JsonNode structured = null;
        if (structuredValue != null) {
            structured = switch (structuredValue) {
                case JsonDocument document -> JsonUtils.parse(document);
                case JsonNode node -> node;
                default -> JsonUtils.parse(JsonUtils.writeString(structuredValue));
            };
            if (content.stream().noneMatch(TextContent.class::isInstance)) {
                blocks.add(toContentBlock(TextContent.of(structured.toString())));
            }
        }
        return new CallToolResult(blocks, structured, isError, meta, COMPLETE, null);
    }

    private static dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ContentBlock toContentBlock(
            ContentBlock domain) {
        return switch (domain) {
            case dev.tachyonmcp.api.server.domain.TextContent t ->
                new dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.TextContent(
                        "text", t.text(), toAnnotations(t.annotations()), JsonUtils.toJsonNodeMap(t.meta()));
            case dev.tachyonmcp.api.server.domain.ImageContent i ->
                new dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ImageContent(
                        "image",
                        i.data(),
                        i.mimeType(),
                        toAnnotations(i.annotations()),
                        JsonUtils.toJsonNodeMap(i.meta()));
            case dev.tachyonmcp.api.server.domain.AudioContent a ->
                new dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.AudioContent(
                        "audio",
                        a.data(),
                        a.mimeType(),
                        toAnnotations(a.annotations()),
                        JsonUtils.toJsonNodeMap(a.meta()));
            case dev.tachyonmcp.api.server.domain.EmbeddedResource e ->
                new dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.EmbeddedResource(
                        "resource",
                        toResourceContents(e.resource()),
                        toAnnotations(e.annotations()),
                        JsonUtils.toJsonNodeMap(e.meta()));
            case dev.tachyonmcp.api.server.domain.ResourceLink r ->
                new dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ResourceLink(
                        "resource_link",
                        r.name(),
                        r.title(),
                        toIcons(r.icons()),
                        r.uri(),
                        r.description(),
                        r.mimeType(),
                        toAnnotations(r.annotations()),
                        r.size(),
                        JsonUtils.toJsonNodeMap(r.meta()));
        };
    }

    private static dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.PromptMessage toPromptMessage(
            PromptMessage domain) {
        var role =
                switch (domain.role()) {
                    case USER -> dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.Role.USER;
                    case ASSISTANT -> dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.Role.ASSISTANT;
                };
        return new dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.PromptMessage(
                role, toContentBlock(domain.content()));
    }

    private static dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ToolAnnotations toToolAnnotations(
            @Nullable ToolAnnotations domain) {
        return domain == null
                ? null
                : new dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.ToolAnnotations(
                        domain.title(),
                        domain.readOnlyHint(),
                        domain.destructiveHint(),
                        domain.idempotentHint(),
                        domain.openWorldHint());
    }

    private static dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.Annotations toAnnotations(
            @Nullable Annotations domain) {
        if (domain == null) return null;
        var audience = domain.audience() == null
                ? null
                : domain.audience().stream()
                        .map(role -> switch (role) {
                            case USER -> dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.Role.USER;
                            case ASSISTANT -> dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.Role.ASSISTANT;
                        })
                        .toList();
        return new dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.Annotations(
                audience, domain.priority(), domain.lastModified());
    }

    private static @Nullable List<dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.Icon> toIcons(
            @Nullable List<dev.tachyonmcp.api.server.domain.Icon> icons) {
        return icons == null
                ? null
                : icons.stream()
                        .map(icon -> new dev.tachyonmcp.core.protocol.mcp.v2026_07_28.models.Icon(
                                icon.src(), icon.mimeType(), icon.sizes(), icon.theme()))
                        .toList();
    }

    private static JsonNode encodeToTree(Implementation implementation) {
        try (var out = new ByteArrayOutputStream(256);
                var gen = JsonUtils.FACTORY.createGenerator(ObjectWriteContext.empty(), out, JsonEncoding.UTF8)) {
            CodecRegistry.<Implementation>codecFor(Implementation.class).encode(gen, implementation);
            gen.flush();
            return JsonUtils.parseJsonNode(out.toString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode Implementation", e);
        }
    }

    private static <T> void register(Class<T> type, Codec<T> codec) {
        dev.tachyonmcp.core.protocol.mcp.v2025_11_25.codecs.CodecRegistry.registerOverride(
                type, new dev.tachyonmcp.core.protocol.mcp.v2025_11_25.codecs.Codec<>() {
                    @Override
                    public T decode(JsonParser parser) throws IOException {
                        return codec.decode(parser);
                    }

                    @Override
                    public void encode(JsonGenerator generator, T value) throws IOException {
                        codec.encode(generator, value);
                    }
                });
    }
}
