/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import static dev.tachyonmcp.transport.netty.InteractionHandler.INTERACTION_CONTEXT_KEY;
import static io.netty.channel.ChannelFutureListener.CLOSE;

import dev.tachyonmcp.runtime.InteractionContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ChannelHandlerUtils {

    private ChannelHandlerUtils() {}

    public static InteractionContext<?> interactionContext(ChannelHandlerContext ctx) {
        return Objects.requireNonNull(
                ctx.channel().attr(INTERACTION_CONTEXT_KEY).get(),
                "InteractionContext is null. Check if InteractionHandler is configured correctly.");
    }

    public static void sendAccepted(ChannelHandlerContext ctx, @Nullable String origin) {
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        if (origin != null) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }
        ctx.writeAndFlush(response);
    }

    /**
     * Writes a {@code text/plain} response and closes the connection. The response carries
     * {@code Connection: close} so the client does not return the socket to its keep-alive pool
     * (reusing a socket the server is about to close causes intermittent "other side closed"
     * errors, e.g. with undici on Linux). Prefer this over {@code sendPlainText(...).addListener(CLOSE)}.
     */
    public static ChannelFuture sendPlainTextAndClose(
            ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        return sendPlainTextAndClose(ctx, status, message, null);
    }

    /** {@link #sendPlainTextAndClose(ChannelHandlerContext, HttpResponseStatus, String)} echoing {@code origin}. */
    public static ChannelFuture sendPlainTextAndClose(
            ChannelHandlerContext ctx, HttpResponseStatus status, String message, @Nullable String origin) {
        return sendResponse(ctx, status, "text/plain", ByteBufUtil.writeUtf8(ctx.alloc(), message), true, origin);
    }

    /**
     * Writes a response with the given content type and body, then closes the connection with a
     * {@code Connection: close} header. See {@link #sendPlainTextAndClose} for why the header matters.
     */
    public static ChannelFuture sendResponseAndClose(
            ChannelHandlerContext ctx,
            HttpResponseStatus status,
            String contentType,
            ByteBuf body,
            @Nullable String origin) {
        return sendResponse(ctx, status, contentType, body, true, origin);
    }

    public static void sendPlainText(
            ChannelHandlerContext ctx,
            HttpResponseStatus status,
            String message,
            boolean close,
            @Nullable String origin) {
        sendResponse(ctx, status, "text/plain", ByteBufUtil.writeUtf8(ctx.alloc(), message), close, origin);
    }

    public static ChannelFuture sendResponse(
            ChannelHandlerContext ctx,
            HttpResponseStatus status,
            String contentType,
            ByteBuf body,
            boolean close,
            @Nullable String origin) {
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, body);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes());
        if (close) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }
        if (origin != null) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }

        ChannelFuture channelFuture = ctx.writeAndFlush(response);
        if (close) {
            return channelFuture.addListener(CLOSE);
        } else {
            return channelFuture;
        }
    }
}
