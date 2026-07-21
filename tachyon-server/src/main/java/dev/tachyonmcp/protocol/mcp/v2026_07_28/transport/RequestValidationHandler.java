/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2026_07_28.transport;

import dev.tachyonmcp.protocol.mcp.McpHeaderNames;
import dev.tachyonmcp.protocol.mcp.v2026_07_28.McpProtocol;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ServerError;
import dev.tachyonmcp.server.domain.ServerErrors;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcMessage;
import dev.tachyonmcp.transport.netty.ChannelHandlerUtils;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Validates every 2026-07-28 POST request against the requirements that only apply once a request
 * is self-describing (no protocol-level session): required {@code _meta} fields (SEP-2575), the
 * {@code Mcp-Method}/{@code Mcp-Name}/{@code Mcp-Param-*} headers matching the body (SEP-2243), and
 * rejection of methods the draft removed. Runs after {@code http-aggregator} (needs the parsed
 * body) and before the initialize/operation phase handlers, so a rejected request never reaches
 * dispatch.
 *
 * <p>One instance per server (constructed with that server's {@link ServerEngine} to resolve
 * {@code x-mcp-header} tool-schema annotations for {@code Mcp-Param-*} validation), added to every
 * channel's pipeline unconditionally and no-ops for any request that didn't negotiate 2026-07-28 —
 * see the sibling {@code v2025_11_25.transport.RequestValidationHandler}.
 */
@Sharable
public final class RequestValidationHandler extends ChannelInboundHandlerAdapter {

    private static final Set<String> REMOVED_METHODS =
            Set.of("initialize", "ping", "logging/setLevel", "resources/subscribe", "resources/unsubscribe");
    private static final Set<String> NAME_REQUIRED_METHODS = Set.of("tools/call", "resources/read", "prompts/get");

    private static final String META = "_meta";
    private static final String PROTOCOL_VERSION_KEY = "io.modelcontextprotocol/protocolVersion";
    private static final String CLIENT_INFO_KEY = "io.modelcontextprotocol/clientInfo";
    private static final String CLIENT_CAPABILITIES_KEY = "io.modelcontextprotocol/clientCapabilities";

    private static final String BASE64_PREFIX = "=?base64?";
    private static final String BASE64_SUFFIX = "?=";
    private static final String X_MCP_HEADER = "x-mcp-header";
    private static final String PARAM_HEADER_PREFIX = "Mcp-Param-";

    private final ServerEngine server;

    public RequestValidationHandler(ServerEngine server) {
        this.server = server;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof FullHttpRequest req) || req.method() != HttpMethod.POST) {
            ctx.fireChannelRead(msg);
            return;
        }
        var interaction = ChannelHandlerUtils.getInteractionContext(ctx);
        if (interaction == null || !McpProtocol.VERSION.equals(interaction.protocolVersion())) {
            ctx.fireChannelRead(msg);
            return;
        }

        JsonRpcMessage message;
        try {
            // A duplicate view shares the backing memory but has its own reader index, so peeking
            // here doesn't disturb what the operation/init handler reads from req.content() next.
            message = JsonRpcCodec.parseRequest(req.content().duplicate());
        } catch (RuntimeException e) {
            // Malformed JSON: let the normal parse-error path downstream handle it.
            ctx.fireChannelRead(msg);
            return;
        }
        if (!(message instanceof JsonRpcMessage.Request<?> request)) {
            // Notifications/responses/errors: this revision defines no client-to-client-side
            // notifications over Streamable HTTP; nothing to validate here.
            ctx.fireChannelRead(msg);
            return;
        }

        var rejection = validate(req, request);
        if (rejection != null) {
            reject(ctx, req, request.id(), rejection);
            return;
        }
        ctx.fireChannelRead(msg);
    }

    private @Nullable ServerError validate(FullHttpRequest req, JsonRpcMessage.Request<?> request) {
        var method = request.method();
        if (REMOVED_METHODS.contains(method)) {
            return ServerErrors.methodNotFound("Method not found");
        }

        var paramsMap = request.params() instanceof Map<?, ?> m ? m : Map.of();
        var meta = paramsMap.get(META) instanceof Map<?, ?> mm ? mm : null;
        if (meta == null) {
            return ServerErrors.invalidParams("Missing required " + META);
        }
        if (!(meta.get(PROTOCOL_VERSION_KEY) instanceof String metaProtocolVersion)) {
            return ServerErrors.invalidParams(META + " missing required field: " + PROTOCOL_VERSION_KEY);
        }
        if (!(meta.get(CLIENT_INFO_KEY) instanceof Map<?, ?>)) {
            return ServerErrors.invalidParams(META + " missing required field: " + CLIENT_INFO_KEY);
        }
        if (!(meta.get(CLIENT_CAPABILITIES_KEY) instanceof Map<?, ?>)) {
            return ServerErrors.invalidParams(META + " missing required field: " + CLIENT_CAPABILITIES_KEY);
        }

        var headerProtocolVersion = strip(req.headers().get(McpHeaderNames.MCP_PROTOCOL_VERSION));
        if (!metaProtocolVersion.equals(headerProtocolVersion)) {
            return ServerErrors.headerMismatch("Header mismatch: " + McpHeaderNames.MCP_PROTOCOL_VERSION
                    + " header value '" + headerProtocolVersion + "' does not match body " + META + "."
                    + PROTOCOL_VERSION_KEY + " value '" + metaProtocolVersion + "'");
        }

        var headerMethod = strip(req.headers().get(McpHeaderNames.MCP_METHOD));
        if (!method.equals(headerMethod)) {
            return ServerErrors.headerMismatch("Header mismatch: " + McpHeaderNames.MCP_METHOD + " header value '"
                    + headerMethod + "' does not match body method '" + method + "'");
        }

        if (NAME_REQUIRED_METHODS.contains(method)) {
            var bodyName =
                    "resources/read".equals(method) ? asString(paramsMap.get("uri")) : asString(paramsMap.get("name"));
            var headerName = decodeName(req.headers().get(McpHeaderNames.MCP_NAME));
            if (bodyName == null || headerName == null || !headerName.equals(bodyName)) {
                return ServerErrors.headerMismatch("Header mismatch: " + McpHeaderNames.MCP_NAME + " header value '"
                        + headerName + "' does not match body value '" + bodyName + "'");
            }
        }

        if ("tools/call".equals(method)) {
            var toolCallRejection = validateCustomParamHeaders(req, paramsMap);
            if (toolCallRejection != null) return toolCallRejection;
        }

        return null;
    }

    /**
     * Validates {@code Mcp-Param-{Name}} headers against the tool's {@code x-mcp-header}-annotated
     * input-schema properties (SEP-2243). Only scans top-level {@code properties} — nested reachable
     * chains are a schema-authoring detail this server's tools don't currently need.
     */
    private @Nullable ServerError validateCustomParamHeaders(FullHttpRequest req, Map<?, ?> paramsMap) {
        var toolName = asString(paramsMap.get("name"));
        if (toolName == null) return null;
        var descriptor = server.tools().find(toolName).orElse(null);
        var inputSchema = descriptor != null ? descriptor.inputSchema() : null;
        if (inputSchema == null) return null;
        var properties = inputSchema.path("properties");
        if (!properties.isObject()) return null;

        var arguments = paramsMap.get("arguments") instanceof Map<?, ?> a ? a : Map.of();
        for (var entry : properties.properties()) {
            var headerAnnotation = entry.getValue().path(X_MCP_HEADER);
            if (!headerAnnotation.isString()) continue;
            var propertyName = entry.getKey();
            var headerName = PARAM_HEADER_PREFIX + headerAnnotation.asString();
            var bodyValue = argumentAsHeaderString(arguments.get(propertyName));
            var rawHeader = req.headers().get(headerName);

            if (bodyValue == null) {
                continue; // Parameter not in arguments: client MUST omit the header, server MUST NOT expect it.
            }
            if (rawHeader == null) {
                return ServerErrors.headerMismatch("Header mismatch: " + headerName
                        + " is required because body arguments contains '" + propertyName + "'");
            }
            String decodedHeader;
            try {
                decodedHeader = decodeParamValue(rawHeader);
            } catch (IllegalArgumentException e) {
                return ServerErrors.headerMismatch("Header mismatch: " + headerName + " has invalid Base64 encoding");
            }
            if (!decodedHeader.equals(bodyValue)) {
                return ServerErrors.headerMismatch("Header mismatch: " + headerName + " header value '" + decodedHeader
                        + "' does not match body value '" + bodyValue + "'");
            }
        }
        return null;
    }

    /**
     * String form of an {@code x-mcp-header}-eligible argument value, per the Value Encoding type
     * conversion rules (string as-is, integer as decimal, boolean lowercase); {@code null} if the
     * value is absent/null or not one of those primitive types.
     */
    private static @Nullable String argumentAsHeaderString(@Nullable Object value) {
        return switch (value) {
            case null -> null;
            case String s -> s;
            case Long l -> String.valueOf(l);
            case Integer i -> String.valueOf(i);
            case Boolean b -> String.valueOf(b);
            default -> null;
        };
    }

    private void reject(ChannelHandlerContext ctx, FullHttpRequest req, Object id, ServerError error) {
        InteractionContext interaction = ChannelHandlerUtils.requireInteractionContext(ctx);
        var wireError = interaction.protocol().responseMapper().error(error);
        var body = JsonRpcCodec.serializeError(id, wireError.code(), wireError.message(), wireError.data());
        var origin = req.headers().get(HttpHeaderNames.ORIGIN);
        req.release();
        ChannelHandlerUtils.sendResponseAndClose(
                ctx, HttpResponseStatus.valueOf(wireError.httpStatus()), "application/json", body, origin);
    }

    private static @Nullable String asString(@Nullable Object value) {
        return value instanceof String s ? s : null;
    }

    private static @Nullable String strip(@Nullable String value) {
        return value == null ? null : value.strip();
    }

    /**
     * Decodes the Base64 sentinel format ({@code =?base64?<value>?=}) used to carry non-ASCII or
     * whitespace-containing {@code Mcp-Name} values, and strips OWS from plain values (RFC 9110
     * field-value parsing excludes leading/trailing whitespace before comparison).
     */
    private static @Nullable String decodeName(@Nullable String raw) {
        if (raw == null) return null;
        var trimmed = raw.strip();
        if (trimmed.length() >= BASE64_PREFIX.length() + BASE64_SUFFIX.length()
                && trimmed.startsWith(BASE64_PREFIX)
                && trimmed.endsWith(BASE64_SUFFIX)) {
            var encoded = trimmed.substring(BASE64_PREFIX.length(), trimmed.length() - BASE64_SUFFIX.length());
            try {
                return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return trimmed;
    }

    /**
     * Decodes an {@code Mcp-Param-*} header value: the Base64 sentinel wrapper if present, else the
     * literal value. Unlike {@link #decodeName}, invalid Base64 inside the wrapper is a distinct
     * failure the caller must reject explicitly (SEP-2243: "Server MUST reject requests with invalid
     * Base64 padding or characters"), so this throws rather than degrading to a plain mismatch.
     */
    private static String decodeParamValue(String raw) {
        if (raw.length() >= BASE64_PREFIX.length() + BASE64_SUFFIX.length()
                && raw.startsWith(BASE64_PREFIX)
                && raw.endsWith(BASE64_SUFFIX)) {
            var encoded = raw.substring(BASE64_PREFIX.length(), raw.length() - BASE64_SUFFIX.length());
            // Base64.getDecoder() tolerates missing padding (e.g. "SGVsbG8" for "Hello"), but SEP-2243
            // requires rejecting malformed padding, not just invalid alphabet characters.
            if (encoded.length() % 4 != 0) {
                throw new IllegalArgumentException("invalid Base64 padding");
            }
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        }
        return raw;
    }
}
