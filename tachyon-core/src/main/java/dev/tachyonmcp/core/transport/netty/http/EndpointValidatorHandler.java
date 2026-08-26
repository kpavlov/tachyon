/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.transport.netty.http;

import static dev.tachyonmcp.core.transport.netty.ChannelHandlerUtils.rejectAndClose;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rejects requests whose URI does not match the configured MCP endpoint path
 * with a {@code 404 Not Found} response.
 */
@ChannelHandler.Sharable
public class EndpointValidatorHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointValidatorHandler.class);

    private final String mcpEndpoint;

    public EndpointValidatorHandler(String mcpEndpoint) {
        this.mcpEndpoint = normalizedPath(mcpEndpoint);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest req) {
            var uri = req.uri();

            if (!normalizedPath(uri).equals(mcpEndpoint)) {
                LOGGER.warn("Unknown endpoint: {}", uri);
                rejectAndClose(ctx, msg, HttpResponseStatus.NOT_FOUND, "Not Found");
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }

    private static String normalizedPath(String uri) {
        var queryStart = uri.indexOf('?');
        var path = queryStart < 0 ? uri : uri.substring(0, queryStart);
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
