/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2026_07_28.codecs;

import dev.tachyonmcp.protocol.mcp.v2026_07_28.McpProtocol;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.BlobResourceContents;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.DiscoverResult;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.EmptyResult;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.Implementation;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.ListPromptsResult;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.ListResourceTemplatesResult;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.ListResourcesResult;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.ListToolsResult;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.Prompt;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.ReadResourceResult;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.Resource;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.ResourceContents;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.ResourceTemplate;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.ResultMetaObject;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.TextResourceContents;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.Tool;
import dev.tachyonmcp.server.config.ServerIdentity;
import dev.tachyonmcp.server.domain.ServerError;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.json.JsonUtils;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcError;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
public final class McpResponseMapper extends dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.McpResponseMapper {

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
            List<String> supportedVersions,
            dev.tachyonmcp.server.ServerCapabilities capabilities,
            ServerIdentity serverIdentity) {
        var implementation = ServerInfoMapper.toImplementation(serverIdentity);
        var meta = new ResultMetaObject(implementation);
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
    public Object readResourceResult(List<dev.tachyonmcp.server.domain.ResourceContents> contents) {
        var protocolContents =
                contents.stream().map(McpResponseMapper::toResourceContents).toList();
        return new ReadResourceResult(protocolContents, null, COMPLETE, 0, PUBLIC, null);
    }

    @Override
    public Object listPromptsResult(List<PromptDescriptor> prompts, @Nullable String nextCursor) {
        var protocolPrompts = prompts.stream().map(McpResponseMapper::toPrompt).toList();
        return new ListPromptsResult(protocolPrompts, null, COMPLETE, nextCursor, 0, PUBLIC, null);
    }

    private static Tool toTool(ToolDescriptor d) {
        return new Tool(d.description(), d.inputSchema(), d.outputSchema(), null, null, d.name(), d.title(), null);
    }

    private static Resource toResource(ResourceDescriptor d) {
        return new Resource(d.uri(), d.description(), d.mimeType(), null, d.size(), null, d.name(), d.title(), null);
    }

    private static ResourceTemplate toResourceTemplate(ResourceTemplateDescriptor d) {
        return new ResourceTemplate(
                d.uriTemplate(), d.description(), d.mimeType(), null, null, d.name(), d.title(), null);
    }

    private static ResourceContents toResourceContents(dev.tachyonmcp.server.domain.ResourceContents domain) {
        return switch (domain) {
            case dev.tachyonmcp.server.domain.TextResourceContents t ->
                new TextResourceContents(t.text(), t.uri(), t.mimeType(), null);
            case dev.tachyonmcp.server.domain.BlobResourceContents b ->
                new BlobResourceContents(b.blob(), b.uri(), b.mimeType(), null);
        };
    }

    private static Prompt toPrompt(PromptDescriptor d) {
        return new Prompt(d.description(), null, null, d.name(), d.title(), null);
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
        dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.CodecRegistry.registerOverride(
                type, new dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.Codec<>() {
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
