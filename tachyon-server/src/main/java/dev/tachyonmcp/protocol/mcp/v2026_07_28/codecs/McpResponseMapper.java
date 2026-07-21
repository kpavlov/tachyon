/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2026_07_28.codecs;

import dev.tachyonmcp.protocol.mcp.v2026_07_28.McpProtocol;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.DiscoverResult;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.EmptyResult;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.Icon;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.Implementation;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.models.ServerCapabilities;
import dev.tachyonmcp.server.config.ServerIdentity;
import java.io.IOException;
import java.util.List;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Maps the modern MCP discovery and empty response shapes.
 */
public final class McpResponseMapper extends dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.McpResponseMapper {

    private static final String COMPLETE = "complete";
    private static final String PUBLIC = "public";
    private static final ObjectNode EMPTY_NODE = JsonNodeFactory.instance.objectNode();

    static {
        register(DiscoverResult.class, new DiscoverResultCodec());
        register(EmptyResult.class, new EmptyResultCodec());
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
    public Object discoverResult(
            List<String> supportedVersions,
            dev.tachyonmcp.server.ServerCapabilities capabilities,
            ServerIdentity serverIdentity) {
        return new DiscoverResult(
                supportedVersions,
                capabilities(capabilities),
                implementation(serverIdentity),
                serverIdentity.instructions(),
                null,
                COMPLETE,
                0,
                PUBLIC,
                null);
    }

    private static ServerCapabilities capabilities(dev.tachyonmcp.server.ServerCapabilities capabilities) {
        var builder = ServerCapabilities.builder().experimental(capabilities.experimental());
        if (capabilities.logging()) builder.logging(EMPTY_NODE);
        if (capabilities.completions()) builder.completions(EMPTY_NODE);
        if (capabilities.prompts() != null) {
            builder.prompts(
                    new ServerCapabilities.Prompts(capabilities.prompts().listChanged()));
        }
        if (capabilities.resources() != null) {
            var resources = capabilities.resources();
            builder.resources(new ServerCapabilities.Resources(resources.subscribe(), resources.listChanged()));
        }
        if (capabilities.tools() != null) {
            builder.tools(new ServerCapabilities.Tools(capabilities.tools().listChanged()));
        }
        return builder.build();
    }

    private static Implementation implementation(ServerIdentity identity) {
        var builder = Implementation.builder()
                .name(identity.name())
                .version(identity.version())
                .description(identity.description())
                .title(identity.title())
                .websiteUrl(identity.websiteUrl());
        if (identity.icons() != null && !identity.icons().isEmpty()) {
            builder.icons(identity.icons().stream()
                    .map(icon -> new Icon(icon.src(), icon.mimeType(), icon.sizes(), icon.theme()))
                    .toList());
        }
        return builder.build();
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
