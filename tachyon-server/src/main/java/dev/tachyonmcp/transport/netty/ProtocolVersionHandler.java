/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty;

import static dev.tachyonmcp.transport.netty.ChannelHandlerUtils.sendResponseAndClose;

import dev.tachyonmcp.protocol.Protocol;
import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.protocol.mcp.McpHeaderNames;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.McpProtocol;
import dev.tachyonmcp.server.domain.ServerError;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Negotiates the protocol for each MCP POST and binds it to the interaction context.
 */
@Sharable
public class ProtocolVersionHandler extends ChannelInboundHandlerAdapter {

    private final String mcpEndpoint;
    private static final Protocol LATEST_PROTOCOL;
    private static final List<String> SUPPORTED_VERSIONS;

    static {
        LATEST_PROTOCOL = Protocols.list().stream()
                .max(Comparator.comparing(Protocol::versionString).thenComparingInt(Protocol::priority))
                .orElseThrow();
        SUPPORTED_VERSIONS = Protocols.list().stream()
                .map(Protocol::versionString)
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public ProtocolVersionHandler(String mcpEndpoint) {
        this.mcpEndpoint = mcpEndpoint;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest req
                && req.method() == HttpMethod.POST
                && req.uri().startsWith(mcpEndpoint)) {
            var protoVersion = req.headers().get(McpHeaderNames.MCP_PROTOCOL_VERSION);
            var protocol = Protocols.resolve(req);
            if (protocol.isEmpty()) {
                var requestedVersion = protoVersion != null ? protoVersion : "";
                var error = LATEST_PROTOCOL
                        .responseMapper()
                        .error(new ServerError(
                                ServerError.Kind.UNSUPPORTED_PROTOCOL_VERSION,
                                "Unsupported protocol version",
                                Map.of("supported", SUPPORTED_VERSIONS, "requested", requestedVersion)));
                var body = JsonRpcCodec.serializeError(-1, error.code(), error.message(), error.data());
                var origin = req.headers().get(HttpHeaderNames.ORIGIN);
                sendResponseAndClose(ctx, HttpResponseStatus.BAD_REQUEST, "application/json", body, origin);
                return;
            }
            var interaction = ctx.channel().attr(InteractionHandler.INTERACTION_CONTEXT_KEY);
            if (McpProtocol.VERSION.equals(protocol.get().versionString())) {
                interaction.set(protocol.get().createInteractionContext());
            } else {
                interaction.setIfAbsent(protocol.get().createInteractionContext());
            }
        }
        ctx.fireChannelRead(msg);
    }
}
