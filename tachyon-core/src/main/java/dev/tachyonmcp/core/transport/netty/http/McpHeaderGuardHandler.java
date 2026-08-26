/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.transport.netty.http;

import static dev.tachyonmcp.core.transport.netty.ChannelHandlerUtils.rejectAndClose;
import static io.netty.util.AsciiString.contentEqualsIgnoreCase;

import dev.tachyonmcp.core.protocol.Protocol;
import dev.tachyonmcp.core.protocol.Protocols;
import dev.tachyonmcp.core.protocol.mcp.McpHeaderNames;
import dev.tachyonmcp.core.protocol.mcp.v2026_07_28.McpProtocol;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the HTTP view of an MCP request and the executed view from diverging, rejecting anything
 * that lets them with {@code 400 Bad Request}.
 *
 * <p>SEP-2243 mirrors routing-critical values ({@code Mcp-Method}, {@code Mcp-Name},
 * {@code Mcp-Param-*}) into HTTP headers precisely so gateways, WAFs and rate limiters can decide
 * without parsing the JSON body. That only holds while both views agree, which two things break:
 *
 * <ul>
 *   <li><b>A repeated field line.</b> Netty's {@code HttpHeaders#get} takes the first value, while
 *       an intermediary taking the last one sees something else. For {@code MCP-Protocol-Version}
 *       the disagreement is a downgrade — the server negotiates the first value and skips the newer
 *       revision's validation while the proxy believes the newer revision applied.
 *   <li><b>A mirrored header on a request that does not negotiate 2026-07-28.</b> Header/body
 *       agreement is enforced only by that revision's {@code RequestValidationHandler}; on any
 *       older version the mirrors are never compared to the body, so {@code Mcp-Method: tools/list}
 *       would route past a gateway while the body ran {@code tools/call}. The mirrors were
 *       introduced by 2026-07-28, so no legitimate older client sends them.
 * </ul>
 *
 * <p>Runs before {@code http-aggregator}, so no {@code get()} downstream can be misled, and applies
 * to every HTTP method: {@code MCP-Session-Id} and {@code Last-Event-ID} matter on GET/DELETE too.
 * Rejection is therefore plain text rather than JSON-RPC {@code -32020}: the body carrying the id to
 * echo hasn't been assembled yet, and malformed transport metadata is not an MCP-level mismatch.
 * Same treatment duplicate {@code Host}/{@code Origin} already get in
 * {@link DnsRebindingProtectionHandler}.
 *
 * <p>Duplicates are rejected even when both values are identical — otherwise an intermediary can
 * still disagree about whether the field is singular.
 */
@Sharable
public final class McpHeaderGuardHandler extends ChannelInboundHandlerAdapter {

    /** Shared instance: the handler is stateless. */
    public static final McpHeaderGuardHandler INSTANCE = new McpHeaderGuardHandler();

    private static final AsciiString PROTOCOL_VERSION = AsciiString.cached(McpHeaderNames.MCP_PROTOCOL_VERSION);
    private static final AsciiString SESSION_ID = AsciiString.cached(McpHeaderNames.MCP_SESSION_ID);
    private static final AsciiString METHOD = AsciiString.cached(McpHeaderNames.MCP_METHOD);
    private static final AsciiString NAME = AsciiString.cached(McpHeaderNames.MCP_NAME);
    private static final AsciiString LAST_EVENT_ID = AsciiString.cached(McpHeaderNames.LAST_EVENT_ID);

    private static final Set<String> SUPPORTED_VERSIONS =
            Protocols.list().stream().map(Protocol::versionString).collect(Collectors.toUnmodifiableSet());

    private static final String DUPLICATE_MESSAGE = "Duplicate MCP header";
    private static final String VERSION_MESSAGE =
            "SEP-2243 headers require " + McpHeaderNames.MCP_PROTOCOL_VERSION + ": " + McpProtocol.VERSION;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest req) {
            // The offending header name is deliberately not echoed: it is client-controlled input.
            var rejection = validate(req.headers());
            if (rejection != null) {
                rejectAndClose(ctx, msg, HttpResponseStatus.BAD_REQUEST, rejection);
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }

    /**
     * The rejection reason, or {@code null} when the request is well-formed. {@code names()} keeps
     * case variants as distinct entries while {@code getAll} matches case-insensitively, so
     * {@code Mcp-Method} plus {@code mcp-method} still collapses into a duplicate.
     */
    private static @Nullable String validate(HttpHeaders headers) {
        var sawMirror = false;
        for (var name : headers.names()) {
            var mirror = isMirrored(name);
            if (!mirror && !isSingleton(name)) {
                continue;
            }
            if (headers.getAll(name).size() > 1) {
                return DUPLICATE_MESSAGE;
            }
            sawMirror |= mirror;
        }
        // Safe to read now: a duplicated version header would already have been rejected above.
        return sawMirror ? mirrorsWithoutValidation(headers.get(PROTOCOL_VERSION)) : null;
    }

    /**
     * Whether mirrors on this request would go unchecked. A version this server does not support is
     * left alone: {@code UnsupportedProtocolVersionHandler} answers it with the more specific
     * {@code -32022}, and preempting that with a bare 400 loses the supported-version list. An absent
     * header negotiates an older revision, so its mirrors are unvalidated all the same.
     */
    private static @Nullable String mirrorsWithoutValidation(@Nullable String version) {
        if (McpProtocol.VERSION.equals(version)) {
            return null;
        }
        return version == null || SUPPORTED_VERSIONS.contains(version) ? VERSION_MESSAGE : null;
    }

    /** The SEP-2243 mirrors of body values, meaningful only under {@link McpProtocol#VERSION}. */
    private static boolean isMirrored(CharSequence name) {
        return contentEqualsIgnoreCase(name, METHOD)
                || contentEqualsIgnoreCase(name, NAME)
                || McpHeaderNames.isParamHeader(name);
    }

    /** MCP headers that carry exactly one value on any protocol version. */
    private static boolean isSingleton(CharSequence name) {
        return contentEqualsIgnoreCase(name, PROTOCOL_VERSION)
                || contentEqualsIgnoreCase(name, SESSION_ID)
                || contentEqualsIgnoreCase(name, LAST_EVENT_ID);
    }
}
