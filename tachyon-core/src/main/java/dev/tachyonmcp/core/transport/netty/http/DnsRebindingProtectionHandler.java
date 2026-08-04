/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.transport.netty.http;

import static dev.tachyonmcp.core.transport.netty.ChannelHandlerUtils.sendPlainTextAndClose;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guards against DNS-rebinding attacks by validating the {@code Host} and {@code Origin} headers.
 *
 * <p>Both headers, when present, must resolve to {@code localhost} or {@code 127.0.0.1} (any
 * scheme, any port). Requests without an {@code Origin} header (non-browser clients) are passed
 * through unchanged. Invalid requests receive {@code 403 Forbidden} and the connection is closed.
 *
 * <p>An optional {@code allowedHosts} allowlist extends the accepted {@code Host} authorities beyond
 * localhost — e.g. {@code "host.docker.internal:8096"} so a sanctioned server reached over a Docker
 * bridge is not rejected. Entries are matched case-insensitively against the request's full
 * authority ({@code host} or {@code host:port}) and against its host part with the port stripped, so
 * either {@code "host.docker.internal"} or {@code "host.docker.internal:8096"} accepts a request
 * whose {@code Host} is {@code host.docker.internal:8096}. The default (empty allowlist) preserves
 * the localhost-only behaviour.
 */
@ChannelHandler.Sharable
public class DnsRebindingProtectionHandler extends ChannelInboundHandlerAdapter {

    private final Set<String> allowedHosts;

    /** Localhost-only protection (no additional allowed hosts). */
    public DnsRebindingProtectionHandler() {
        this(List.of());
    }

    /**
     * @param allowedHosts additional {@code Host} authorities to accept beyond localhost/127.0.0.1;
     *     matched case-insensitively against the full authority and its host part
     */
    public DnsRebindingProtectionHandler(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts.stream()
                .filter(h -> h != null && !h.isEmpty())
                .map(h -> h.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest req) {
            var origin = req.headers().getAsString(HttpHeaderNames.ORIGIN);
            if (origin != null && !origin.isEmpty() && !isLocalhostOrigin(origin)) {
                sendPlainTextAndClose(ctx, HttpResponseStatus.FORBIDDEN, "Forbidden");
                return;
            }
            var host = req.headers().getAsString(HttpHeaderNames.HOST);
            if (host != null && !host.isEmpty() && !isLocalhostAuthority(host) && !isAllowedHost(host)) {
                sendPlainTextAndClose(ctx, HttpResponseStatus.FORBIDDEN, "Forbidden");
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }

    /** Returns {@code true} when {@code authority} matches a configured allowed host. */
    private boolean isAllowedHost(String authority) {
        if (allowedHosts.isEmpty()) {
            return false;
        }
        var lower = authority.toLowerCase(Locale.ROOT);
        if (allowedHosts.contains(lower)) {
            return true;
        }
        var hostOnly = lower.startsWith("[") ? lower : stripPort(lower);
        return allowedHosts.contains(hostOnly);
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
