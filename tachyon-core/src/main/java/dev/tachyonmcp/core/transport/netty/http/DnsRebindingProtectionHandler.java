/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.transport.netty.http;

import static dev.tachyonmcp.core.transport.netty.ChannelHandlerUtils.sendPlainTextAndClose;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guards against DNS-rebinding attacks by validating the {@code Host} and {@code Origin} headers.
 * Rejected requests receive {@code 403 Forbidden} and the connection is closed.
 *
 * <p><b>Host.</b> A request's {@code Host} authority must be {@code localhost}/{@code 127.0.0.1} or
 * match one of the configured {@link #DnsRebindingProtectionHandler(List) allowedHosts}. The guard
 * fails closed: a request carrying <em>multiple</em> {@code Host} headers is rejected (RFC&nbsp;7230
 * §5.4), and a missing/blank {@code Host} is rejected on HTTP/1.1+ (where {@code Host} is mandatory).
 * HTTP/1.0, where {@code Host} is optional, is exempt from the missing-Host rule.
 *
 * <p><b>Origin.</b> When present, {@code Origin} must be localhost/{@code 127.0.0.1}. {@code
 * allowedHosts} does <em>not</em> widen this: a browser page whose {@code Origin} is a non-local host
 * (e.g. {@code http://host.docker.internal:3000}) is still rejected. {@code allowedHosts} only helps
 * non-browser clients — which send no {@code Origin} — reach a server whose {@code Host} is non-local
 * (e.g. a container reaching the host via {@code host.docker.internal}).
 *
 * <p><b>{@code allowedHosts} semantics.</b> Each entry is a bare <em>authority</em> (a host, or
 * {@code host:port}) — not a URL. Matching is case-insensitive.
 * <ul>
 *   <li>{@code "example.com"} — that host on <em>any</em> port.</li>
 *   <li>{@code "example.com:8096"} — only that {@code host:port}.</li>
 *   <li>IPv6 literals must be bracketed: {@code "[2001:db8::1]"} or {@code "[2001:db8::1]:8096"}.</li>
 * </ul>
 * Entries are trimmed; an entry that is blank, or contains whitespace, control characters, or URL
 * syntax (scheme, path, user-info, query, or fragment) is rejected with {@link
 * IllegalArgumentException}. The default (empty allowlist) preserves the localhost-only behaviour.
 */
@ChannelHandler.Sharable
public class DnsRebindingProtectionHandler extends ChannelInboundHandlerAdapter {

    private final Set<String> allowedHosts;

    /** Localhost-only protection (no additional allowed hosts). */
    public DnsRebindingProtectionHandler() {
        this(List.of());
    }

    /**
     * @param allowedHosts additional {@code Host} authorities to accept beyond localhost/127.0.0.1
     *     (see the class documentation for entry syntax and matching rules)
     * @throws IllegalArgumentException if an entry is not a bare host/{@code host:port} authority
     */
    public DnsRebindingProtectionHandler(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(h -> !h.isEmpty())
                .map(DnsRebindingProtectionHandler::validateHostEntry)
                .map(h -> h.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest req) {
            var origin = req.headers().getAsString(HttpHeaderNames.ORIGIN);
            if (origin != null && !origin.isEmpty() && !isLocalhostOrigin(origin)) {
                reject(ctx, msg);
                return;
            }
            var hosts = req.headers().getAll(HttpHeaderNames.HOST);
            if (hosts.size() > 1) {
                // Multiple Host headers are ambiguous and a request-smuggling vector — reject.
                reject(ctx, msg);
                return;
            }
            var host = hosts.isEmpty() ? null : hosts.get(0);
            if (host == null || host.isBlank()) {
                // HTTP/1.1+ mandates Host; a DNS-rebinding guard must fail closed on its absence.
                // HTTP/1.0 permits omitting it, so it is exempt.
                if (req.protocolVersion().compareTo(HttpVersion.HTTP_1_1) >= 0) {
                    reject(ctx, msg);
                    return;
                }
            } else if (!isLocalhostAuthority(host) && !isAllowedHost(host)) {
                reject(ctx, msg);
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }

    /**
     * Rejects the request with {@code 403 Forbidden} and closes the connection. Releases the inbound
     * message first: it is not forwarded down the pipeline, so nothing else will free its buffers.
     */
    private static void reject(ChannelHandlerContext ctx, Object msg) {
        ReferenceCountUtil.release(msg);
        sendPlainTextAndClose(ctx, HttpResponseStatus.FORBIDDEN, "Forbidden");
    }

    /** Returns {@code true} when {@code authority} matches a configured allowed host. */
    private boolean isAllowedHost(String authority) {
        if (allowedHosts.isEmpty()) {
            return false;
        }
        var lower = authority.toLowerCase(Locale.ROOT);
        return allowedHosts.contains(lower) || allowedHosts.contains(stripPort(lower));
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
        var host = stripPort(authority);
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1");
    }

    /**
     * Strips a trailing {@code :port} from an authority, leaving the host. Handles bracketed IPv6
     * literals ({@code [::1]:8096} -> {@code [::1]}); the colons inside the brackets are not ports.
     */
    private static String stripPort(String authority) {
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            return close >= 0 ? authority.substring(0, close + 1) : authority;
        }
        int colon = authority.lastIndexOf(':');
        return colon >= 0 ? authority.substring(0, colon) : authority;
    }

    /** Validates a trimmed, non-empty allowlist entry is a bare authority; throws otherwise. */
    private static String validateHostEntry(String entry) {
        for (int i = 0; i < entry.length(); i++) {
            char c = entry.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                throw new IllegalArgumentException(
                        "allowedHosts entry must not contain whitespace or control characters: '" + entry + "'");
            }
        }
        if (entry.indexOf('/') >= 0 || entry.indexOf('@') >= 0 || entry.indexOf('?') >= 0 || entry.indexOf('#') >= 0) {
            throw new IllegalArgumentException(
                    "allowedHosts entry must be a bare host or host:port authority, not a URL: '" + entry + "'");
        }
        return entry;
    }
}
