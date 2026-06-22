/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty.http;

import static dev.tachyonmcp.transport.netty.ChannelHandlerUtils.sendPlainTextAndClose;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Guards against DNS-rebinding attacks by validating the {@code Host} and {@code Origin} headers.
 *
 * <p>Both headers, when present, must resolve to {@code localhost} or {@code 127.0.0.1} (any
 * scheme, any port). Requests without an {@code Origin} header (non-browser clients) are passed
 * through unchanged. Invalid requests receive {@code 403 Forbidden} and the connection is closed.
 */
@ChannelHandler.Sharable
public class DnsRebindingProtectionHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest req) {
            var origin = req.headers().getAsString(HttpHeaderNames.ORIGIN);
            if (origin != null && !origin.isEmpty() && !isLocalhostOrigin(origin)) {
                sendPlainTextAndClose(ctx, HttpResponseStatus.FORBIDDEN, "Forbidden");
                return;
            }
            var host = req.headers().getAsString(HttpHeaderNames.HOST);
            if (host != null && !host.isEmpty() && !isLocalhostAuthority(host)) {
                sendPlainTextAndClose(ctx, HttpResponseStatus.FORBIDDEN, "Forbidden");
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }

    /** Returns {@code true} when the origin's host part is {@code localhost} or {@code 127.0.0.1}. */
    static boolean isLocalhostOrigin(String origin) {
        int sep = origin.indexOf("//");
        if (sep < 0) return false;
        var authority = origin.substring(sep + 2);
        // Strip any path
        int slash = authority.indexOf('/');
        if (slash >= 0) authority = authority.substring(0, slash);
        return isLocalhostAuthority(authority);
    }

    /** Returns {@code true} when {@code authority} ({@code host} or {@code host:port}) is localhost. */
    static boolean isLocalhostAuthority(String authority) {
        if (authority.isEmpty()) return false;
        // Strip port: last colon for host:port; skip bracketed IPv6 addresses
        var host = authority.startsWith("[") ? authority : stripPort(authority);
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1");
    }

    private static String stripPort(String authority) {
        int colon = authority.lastIndexOf(':');
        return colon >= 0 ? authority.substring(0, colon) : authority;
    }
}
