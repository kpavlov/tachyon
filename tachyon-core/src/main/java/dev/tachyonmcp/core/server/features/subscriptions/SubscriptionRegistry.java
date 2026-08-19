/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.subscriptions;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.domain.RequestId;
import dev.tachyonmcp.core.protocol.ProtocolRequestMapper.SubscriptionListenRequest;
import dev.tachyonmcp.core.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.core.runtime.SseEvent;
import dev.tachyonmcp.core.server.OutboundSseStream;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.transport.jsonrpc.JsonRpcCodec;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Tracks active {@code subscriptions/listen} streams (2026-07-28), independent of any session,
 * and pushes {@code notifications/tools|prompts|resources/list_changed} and {@code
 * notifications/resources/updated} to each stream whose filter opted in.
 *
 * <p>Keyed by an internal counter, not the wire {@code subscriptionId} — two different stateless
 * connections may legally reuse the same JSON-RPC request id, so the registry's own key and the id
 * echoed back to clients are kept distinct.
 */
@InternalApi
public final class SubscriptionRegistry {

    /** A live {@code subscriptions/listen} stream: its filter, transport, and deferred response. */
    private record Entry(
            RequestId subscriptionId,
            OutboundSseStream stream,
            SubscriptionListenRequest filter,
            ProtocolResponseMapper responseMapper,
            CompletableFuture<Object> pendingResponse) {}

    private final ServerEngine server;
    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong nextKey = new AtomicLong();

    public SubscriptionRegistry(ServerEngine server) {
        this.server = server;
    }

    /** Registers a new subscription, returning its registry key for a later {@link #remove}. */
    public long add(
            RequestId subscriptionId,
            OutboundSseStream stream,
            SubscriptionListenRequest filter,
            ProtocolResponseMapper responseMapper,
            CompletableFuture<Object> pendingResponse) {
        var key = nextKey.incrementAndGet();
        entries.put(key, new Entry(subscriptionId, stream, filter, responseMapper, pendingResponse));
        return key;
    }

    /** Removes a subscription without completing its response, e.g. on client disconnect. */
    public void remove(long key) {
        entries.remove(key);
    }

    public void notifyToolsListChanged() {
        pushListChanged(SubscriptionListenRequest::toolsListChanged, "notifications/tools/list_changed");
    }

    public void notifyPromptsListChanged() {
        pushListChanged(SubscriptionListenRequest::promptsListChanged, "notifications/prompts/list_changed");
    }

    public void notifyResourcesListChanged() {
        pushListChanged(SubscriptionListenRequest::resourcesListChanged, "notifications/resources/list_changed");
    }

    /** Pushes {@code notifications/resources/updated} to every subscription that opted into {@code uri}. */
    public void notifyResourceUpdated(String uri) {
        for (var entry : entries.values()) {
            if (!entry.filter().resourceSubscriptions().contains(uri)) continue;
            push(
                    entry,
                    "notifications/resources/updated",
                    entry.responseMapper().subscriptionResourceUpdatedParams(entry.subscriptionId(), uri));
        }
    }

    private void pushListChanged(Predicate<SubscriptionListenRequest> wants, String method) {
        for (var entry : entries.values()) {
            if (!wants.test(entry.filter())) continue;
            push(entry, method, entry.responseMapper().subscriptionListChangedParams(entry.subscriptionId()));
        }
    }

    private void push(Entry entry, String method, Object params) {
        var notificationJson = JsonRpcCodec.serializeNotificationAsString(method, JsonRpcCodec.toJsonParams(params));
        var sseEvent = new SseEvent(
                ServerEngine.wireEventId(server.nextEventId(), entry.stream().streamKey()),
                "message",
                notificationJson);
        entry.stream().writeEvent(sseEvent);
    }

    /**
     * Gracefully tears down every open subscription: completes each deferred response with a
     * protocol-specific {@code resultType: "complete"} result, which the dispatcher then writes as
     * the stream's final response before closing it. Called on server shutdown.
     */
    public void closeAll() {
        for (var key : List.copyOf(entries.keySet())) {
            var entry = entries.remove(key);
            if (entry == null) continue;
            entry.pendingResponse()
                    .complete(entry.responseMapper().subscriptionsListenGracefulResult(entry.subscriptionId()));
        }
    }
}
