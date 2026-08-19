/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.features.subscriptions.SubscriptionRegistry;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/**
 * Handles MCP 2026-07-28's {@code subscriptions/listen} (replaces {@code resources/subscribe} and
 * the plain HTTP GET stream, SEP-2575): acknowledges the subscription on its request-scoped SSE
 * stream, registers it with {@link SubscriptionRegistry}, and defers the JSON-RPC response until
 * the client disconnects (no response) or the server shuts down (graceful {@code
 * resultType: "complete"} response) — see {@link SubscriptionRegistry#closeAll()}.
 */
@InternalApi
public final class SubscriptionsListenHandler implements RpcMethodHandler {

    private final SubscriptionRegistry registry;

    public SubscriptionsListenHandler(SubscriptionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String method() {
        return "subscriptions/listen";
    }

    @Override
    public Object handle(DispatchContext context, @Nullable Object params) {
        throw new UnsupportedOperationException("subscriptions/listen is stream-based; see handleAsync");
    }

    @Override
    public CompletionStage<Object> handleAsync(DispatchContext context, @Nullable Object params) {
        var requestMapper = context.requestMapper();
        if (!requestMapper.supportsSubscriptionsListen()) {
            return CompletableFuture.completedFuture(ServerErrors.methodNotFound("Method not found"));
        }
        var stream = context.outboundStream();
        if (stream == null) {
            return CompletableFuture.completedFuture(ServerErrors.internalError("No SSE stream available"));
        }
        var subscriptionId = context.requestId();
        if (subscriptionId == null) {
            throw new IllegalStateException("subscriptions/listen dispatched without a request id");
        }
        var filter = requestMapper.subscriptionsListen(params);

        stream.start();
        var pending = new CompletableFuture<>();
        var key = registry.activate(subscriptionId, stream, filter, context.responseMapper(), pending);
        stream.onClose(() -> {
            registry.remove(key);
            pending.cancel(false);
        });
        return pending;
    }
}
