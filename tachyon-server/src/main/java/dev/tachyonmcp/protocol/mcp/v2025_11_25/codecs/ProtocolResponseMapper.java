/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.EmptyResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeResult;
import java.util.LinkedHashMap;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public final class ProtocolResponseMapper {

    private ProtocolResponseMapper() {}

    @Nullable
    public static Object toWireResult(Object domainResult) {
        if (domainResult == null) {
            return null;
        }
        return switch (domainResult) {
            case dev.tachyonmcp.server.domain.EmptyResult r -> new EmptyResult(null, null);
            case dev.tachyonmcp.server.domain.InitializeResponse r -> toInitializeResult(r);
            default -> null;
        };
    }

    private static InitializeResult toInitializeResult(dev.tachyonmcp.server.domain.InitializeResponse domain) {
        var caps = mapCapabilities(domain);
        var info = ServerInfoMapper.toImplementation(domain.serverIdentity());
        return new InitializeResult(domain.protocolVersion(), caps, info, domain.instructions(), null, null);
    }

    private static dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ServerCapabilities mapCapabilities(
            dev.tachyonmcp.server.domain.InitializeResponse domain) {
        var builder = ServerInfoMapper.toServerCapabilities(domain.capabilities());
        if (domain.negotiatedExtensions() != null
                && !domain.negotiatedExtensions().isEmpty()) {
            var exts = new LinkedHashMap<String, JsonNode>();
            exts.putAll(domain.negotiatedExtensions());
            builder.extensions(exts);
        }
        return builder.build();
    }
}
