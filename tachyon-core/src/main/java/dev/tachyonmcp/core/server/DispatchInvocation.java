/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.RequestId;
import dev.tachyonmcp.api.server.interceptor.McpInvocation;
import dev.tachyonmcp.core.server.session.DispatchContext;
import dev.tachyonmcp.core.transport.jsonrpc.JsonRpcCodec;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * {@link McpInvocation} backed by the live {@link DispatchContext} of one dispatch. Session id and
 * protocol version are read through the context on each call, so an invocation handed to an
 * interceptor before {@code initialize} established a session reports the session once it exists.
 */
@InternalApi
final class DispatchInvocation implements McpInvocation {

    private final String method;
    private final DispatchContext context;
    private final @Nullable Object rawParams;

    private volatile @Nullable JsonDocument params;

    DispatchInvocation(String method, DispatchContext context, @Nullable Object rawParams) {
        this.method = method;
        this.context = context;
        this.rawParams = rawParams;
    }

    /**
     * Encodes raw JSON-RPC {@code params} as a JSON string, or {@code null} when the value carries
     * nothing serializable (absent params, or a scalar the wire never produces here).
     */
    static @Nullable String encodeParams(@Nullable Object rawParams) {
        if (rawParams instanceof Map || rawParams instanceof List) {
            return JsonRpcCodec.writeValueAsString(rawParams);
        }
        return rawParams instanceof String s ? s : null;
    }

    @Override
    public String method() {
        return method;
    }

    @Override
    public @Nullable RequestId requestId() {
        return context.requestId();
    }

    @Override
    public @Nullable String sessionId() {
        return context.sessionId();
    }

    @Override
    public String protocolVersion() {
        return context.protocolVersion();
    }

    @Override
    public Optional<String> targetName() {
        return stringParam("name");
    }

    @Override
    public Optional<String> resourceUri() {
        return stringParam("uri");
    }

    private Optional<String> stringParam(String key) {
        return rawParams instanceof Map<?, ?> map && map.get(key) instanceof String value && !value.isBlank()
                ? Optional.of(value)
                : Optional.empty();
    }

    @Override
    public Optional<JsonDocument> params() {
        var cached = params;
        if (cached == null) {
            final var json = encodeParams(rawParams);
            if (json == null || json.isBlank()) {
                // Nothing to cache. Recomputing costs two instanceof checks, so a param-less
                // operation pays less for the miss than a second field would cost every request.
                return Optional.empty();
            }
            cached = JsonDocument.of(json);
            params = cached;
        }
        return Optional.of(cached);
    }

    @Override
    public InteractionContext context() {
        return context;
    }

    @Override
    public String toString() {
        return "McpInvocation[method=" + method + ", requestId=" + requestId() + ", sessionId=" + sessionId() + "]";
    }
}
