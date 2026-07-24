/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.transport;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Placeholder for 2025-11-25 per-request validation, mirroring
 * {@code v2026_07_28.transport.RequestValidationHandler} in the pipeline (see
 * {@code McpChannelInitializer}). 2025-11-25 has no per-request {@code _meta}/header requirements
 * of its own today — session establishment already covers protocol-version and capability
 * negotiation — so this is a no-op. Kept as a real class, not folded away, so a future 2025-11-25
 * requirement has an obvious, symmetric home instead of leaking into the 2026-07-28 handler or the
 * shared dispatcher.
 */
@Sharable
public final class RequestValidationHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ctx.fireChannelRead(msg);
    }
}
