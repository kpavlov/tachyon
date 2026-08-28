/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.core.protocol.ProtocolRequestMapper;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.features.subscriptions.SubscriptionRegistry;
import dev.tachyonmcp.core.server.features.tasks.TasksExtension;
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
public final class SubscriptionsListenHandler
        implements RpcMethodHandler<ProtocolRequestMapper.SubscriptionListenRequest, Object> {

    private final SubscriptionRegistry registry;

    public SubscriptionsListenHandler(SubscriptionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String method() {
        return "subscriptions/listen";
    }

    @Override
    public ProtocolRequestMapper.SubscriptionListenRequest decode(DispatchContext context, @Nullable Object rawParams) {
        var requestMapper = context.requestMapper();
        if (!requestMapper.supportsSubscriptionsListen()) {
            throw new RequestMappingException(ServerErrors.methodNotFound("Method not found"));
        }
        var request = requestMapper.subscriptionsListen(rawParams);
        if (!request.taskIds().isEmpty()) {
            var missingCapability = TasksExtension.requireDeclared(context);
            if (missingCapability != null) {
                throw new RequestMappingException(missingCapability);
            }
        }
        return request;
    }

    @Override
    public Object handle(DispatchContext context, ProtocolRequestMapper.SubscriptionListenRequest filter) {
        throw new UnsupportedOperationException("subscriptions/listen is stream-based; see handleAsync");
    }

    @Override
    public CompletionStage<Object> handleAsync(
            DispatchContext context, ProtocolRequestMapper.SubscriptionListenRequest filter) {
        var stream = context.outboundStream();
        if (stream == null) {
            return CompletableFuture.completedFuture(ServerErrors.internalError("No SSE stream available"));
        }
        var subscriptionId = context.requestId();
        if (subscriptionId == null) {
            throw new IllegalStateException("subscriptions/listen dispatched without a request id");
        }

        var pending = new CompletableFuture<>();
        var key = registry.activate(subscriptionId, stream, filter, context.responseMapper(), pending);
        stream.start();
        stream.onClose(() -> {
            registry.remove(key);
            pending.cancel(false);
        });
        return pending;
    }
}
