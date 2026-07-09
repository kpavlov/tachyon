/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty.http;

import static dev.tachyonmcp.transport.netty.ChannelHandlerUtils.sendPlainTextAndClose;

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
        this.mcpEndpoint = mcpEndpoint;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest req) {
            var uri = req.uri();

            if (!uri.startsWith(mcpEndpoint)) {
                LOGGER.warn("Unknown endpoint: {}", uri);
                sendPlainTextAndClose(ctx, HttpResponseStatus.NOT_FOUND, "Not Found");
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }
}
