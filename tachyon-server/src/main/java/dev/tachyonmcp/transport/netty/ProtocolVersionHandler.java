/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import static dev.tachyonmcp.transport.netty.ChannelHandlerUtils.sendResponseAndClose;

import dev.tachyonmcp.runtime.McpHeaderNames;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import java.util.Set;

/**
 * Validates the {@code MCP-Protocol-Version} header on POST requests.
 * Rejects unsupported versions with a {@code 400 Bad Request} JSON-RPC error.
 */
@Sharable
public class ProtocolVersionHandler extends ChannelInboundHandlerAdapter {

    private static final Set<String> SUPPORTED_VERSIONS =
            Set.of("2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25");

    private final String mcpEndpoint;

    public ProtocolVersionHandler(String mcpEndpoint) {
        this.mcpEndpoint = mcpEndpoint;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest req
                && req.method() == HttpMethod.POST
                && req.uri().startsWith(mcpEndpoint)) {
            var protoVersion = req.headers().get(McpHeaderNames.MCP_PROTOCOL_VERSION);
            if (protoVersion != null && !SUPPORTED_VERSIONS.contains(protoVersion)) {
                var err = JsonRpcErrors.invalidRequest("Unsupported protocol version");
                var body = JsonRpcCodec.serializeError(-1, err.code(), err.message(), null);
                var origin = req.headers().get(HttpHeaderNames.ORIGIN);
                sendResponseAndClose(ctx, HttpResponseStatus.BAD_REQUEST, "application/json", body, origin);
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }
}
