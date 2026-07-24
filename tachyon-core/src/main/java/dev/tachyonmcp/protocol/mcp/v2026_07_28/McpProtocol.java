/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2026_07_28;

import dev.tachyonmcp.protocol.Protocol;
import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.protocol.mcp.McpHeaderNames;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.codecs.McpResponseMapper;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;

/** MCP protocol implementation for the modern, per-request 2026-07-28 revision. */
public final class McpProtocol implements Protocol {

    private static final String ENDPOINT = "/mcp";
    public static final String VERSION = "2026-07-28";
    private static final ProtocolResponseMapper RESPONSE_MAPPER = new McpResponseMapper();

    @Override
    public String endpoint() {
        return ENDPOINT;
    }

    @Override
    public String familyName() {
        return "mcp";
    }

    @Override
    public String versionString() {
        return VERSION;
    }

    @Override
    public boolean matches(HttpRequest request) {
        return request.method() == HttpMethod.POST
                && request.uri().startsWith(endpoint())
                && VERSION.equals(request.headers().get(McpHeaderNames.MCP_PROTOCOL_VERSION));
    }

    @Override
    public ProtocolResponseMapper responseMapper() {
        return RESPONSE_MAPPER;
    }

    /** 2026-07-28 removed protocol-level sessions: every request self-describes via {@code _meta}. */
    @Override
    public boolean supportsSessions() {
        return false;
    }
}
