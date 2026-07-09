/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ReadResourceRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.SubscribeRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.UnsubscribeRequestParams;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.features.ChangeSupport;
import dev.tachyonmcp.server.features.HandlerFutures;
import dev.tachyonmcp.server.features.ListRequests;
import dev.tachyonmcp.server.features.PaginatedResult;
import dev.tachyonmcp.server.features.Pagination;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for resources, templates, and subscriptions.
 */
@InternalApi
public class ResourceRegistry {

    private final ConcurrentHashMap<String, ResourceEntry> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResourceEntry> byUri = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResourceTemplateEntry> templates = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final ServerEngine server;

    private final ChangeSupport changes = new ChangeSupport();

    /**
     * Creates a resource registry bound to the given server (for broadcasting subscription notifications).
     */
    public ResourceRegistry(ServerEngine server) {
        this.server = server;
    }

    public void onChange(Runnable callback) {
        changes.onChange(callback);
    }

    private void fireOnChange() {
        changes.fireOnChange();
    }

    public ResourceRegistry add(ResourceDescriptor descriptor, ResourceHandler handler) {
        var entry = new ResourceEntry(descriptor, handler);
        var previous = byName.put(descriptor.name(), entry);
        if (previous != null && !previous.descriptor().uri().equals(descriptor.uri())) {
            byUri.remove(previous.descriptor().uri());
        }
        byUri.put(descriptor.uri(), entry);
        fireOnChange();
        return this;
    }

    public ResourceRegistry remove(String name) {
        var removed = byName.remove(name);
        if (removed != null) {
            byUri.remove(removed.descriptor().uri());
            fireOnChange();
        }
        return this;
    }

    /**
     * Returns the resource descriptor by name.
     */
    public @Nullable ResourceDescriptor get(String name) {
        var entry = byName.get(name);
        return entry != null ? entry.descriptor() : null;
    }

    public Collection<ResourceDescriptor> getAll() {
        return byName.values().stream().map(ResourceEntry::descriptor).toList();
    }

    public PaginatedResult<ResourceDescriptor> list(int limit, @Nullable String cursor) {
        return list(limit, cursor, Pagination.DEFAULT_PAGE_SIZE, descriptor -> true);
    }

    public PaginatedResult<ResourceDescriptor> list(
            int limit, @Nullable String cursor, Predicate<ResourceDescriptor> filter) {
        return list(limit, cursor, Pagination.DEFAULT_PAGE_SIZE, filter);
    }

    public PaginatedResult<ResourceDescriptor> list(int limit, @Nullable String cursor, int defaultLimit) {
        return list(limit, cursor, defaultLimit, descriptor -> true);
    }

    public PaginatedResult<ResourceDescriptor> list(
            int limit, @Nullable String cursor, int defaultLimit, Predicate<ResourceDescriptor> filter) {
        var all = byName.values().stream()
                .map(ResourceEntry::descriptor)
                .filter(filter)
                .sorted(Comparator.comparing(ResourceDescriptor::name))
                .toList();
        return Pagination.paginate(all, limit, cursor, defaultLimit, ResourceDescriptor::name);
    }

    @Nullable
    ResourceEntry getByUri(String uri) {
        return byUri.get(uri);
    }

    public ResourceRegistry addTemplate(ResourceTemplateEntry template) {
        templates.put(template.name(), template);
        fireOnChange();
        return this;
    }

    public ResourceRegistry removeTemplate(String name) {
        var removed = templates.remove(name);
        if (removed != null) {
            fireOnChange();
        }
        return this;
    }

    private record TemplateMatch(ResourceTemplateEntry entry, Map<String, String> params) {}

    @Nullable
    private TemplateMatch matchTemplate(String uri) {
        return templates.values().stream()
                .sorted(Comparator.comparingInt((ResourceTemplateEntry t) -> -UriTemplatePatterns.VAR
                                .matcher(t.uriTemplate())
                                .replaceAll("")
                                .length())
                        .thenComparing(ResourceTemplateEntry::name))
                .map(template -> {
                    var matcher = template.compiledPattern().matcher(uri);
                    if (!matcher.matches()) return null;
                    var params = new LinkedHashMap<String, String>();
                    for (var name : template.paramNames()) {
                        params.put(name, matcher.group(name));
                    }
                    return new TemplateMatch(template, params);
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public void registerHandlers(Map<String, RpcMethodHandler> registry) {
        registry.put("resources/list", new ResourcesListHandler(this));
        registry.put("resources/templates/list", new ResourcesTemplatesListHandler(this));
        registry.put("resources/read", new ResourcesReadHandler(this));
        registry.put("resources/subscribe", new ResourcesSubscribeHandler(this));
        registry.put("resources/unsubscribe", new ResourcesUnsubscribeHandler(this));
    }

    /**
     * Returns whether the given session is subscribed to the resource URI.
     */
    public boolean isSubscribed(String uri, String sessionId) {
        var subs = subscriptions.get(uri);
        return subs != null && subs.contains(sessionId);
    }

    /**
     * Subscribes the session to the resource URI. Add and removal both run inside the map's
     * per-key operation ({@code compute}/{@code computeIfPresent}), so a subscribe can never land
     * in a set that a concurrent unsubscribe has already unlinked from the map.
     */
    void subscribe(String uri, String sessionId) {
        subscriptions.compute(uri, (k, set) -> {
            if (set == null) {
                set = new CopyOnWriteArraySet<>();
            }
            set.add(sessionId);
            return set;
        });
    }

    /**
     * Removes the session's subscription to the URI, pruning the map entry when it empties.
     */
    void unsubscribe(String uri, String sessionId) {
        subscriptions.computeIfPresent(uri, (k, set) -> {
            set.remove(sessionId);
            return set.isEmpty() ? null : set;
        });
    }

    /**
     * Notifies all subscribed sessions that a resource has been updated.
     */
    public void notifyResourceUpdated(String uri) {
        var subscribedSessionIds = subscriptions.get(uri);
        if (subscribedSessionIds == null || subscribedSessionIds.isEmpty()) {
            return;
        }
        var paramsMap = new LinkedHashMap<String, Object>();
        paramsMap.put("uri", uri);
        for (var sessionId : subscribedSessionIds) {
            server.getSession(sessionId)
                    .ifPresentOrElse(
                            session -> server.sendNotification(session, "notifications/resources/updated", paramsMap),
                            // Session is gone (closed/expired) — nothing ever sweeps its
                            // subscriptions, so drop it lazily here to stop the set growing
                            // with dead ids. Safe while iterating: COW set snapshot.
                            () -> unsubscribe(uri, sessionId));
        }
    }

    private record ResourcesListHandler(ResourceRegistry registry) implements RpcMethodHandler {

        @Override
        public String method() {
            return "resources/list";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var limit = ListRequests.parseLimit(params);
            var cursor = ListRequests.parseCursor(params);
            var paginated = registry.list(limit, cursor, e -> {
                var extId = e.extensionId();
                return extId == null || context.isExtensionEnabled(extId);
            });

            return context.responseMapper().listResourcesResult(paginated.items(), paginated.nextCursor());
        }
    }

    private record ResourcesTemplatesListHandler(ResourceRegistry registry) implements RpcMethodHandler {

        @Override
        public String method() {
            return "resources/templates/list";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var templates = registry.templates.values().stream().toList();
            return context.responseMapper().listResourceTemplatesResult(templates, null);
        }
    }

    private record ResourcesReadHandler(ResourceRegistry registry) implements RpcMethodHandler {

        private static final Logger logger = LoggerFactory.getLogger(ResourcesReadHandler.class);

        @Override
        public String method() {
            return "resources/read";
        }

        /**
         * Runs on the dispatcher's virtual thread; blocking to join the handler is the SPI contract.
         */
        @Override
        public Object handle(DispatchContext context, Object params) throws Exception {
            var readParams = toReadParams(params);
            if (readParams == null) return JsonRpcErrors.invalidRequest("Missing resource URI");
            var uri = readParams.uri();
            var entry = registry.getByUri(uri);
            if (entry != null) {
                var extId = entry.descriptor().extensionId();
                if (extId != null && !context.isExtensionEnabled(extId)) {
                    return JsonRpcErrors.resourceNotFound("Resource not found");
                }
                return readResult(context, uri, () -> entry.handler().readAsync(context, readParams));
            }
            var match = registry.matchTemplate(uri);
            if (match == null) return JsonRpcErrors.resourceNotFound("Resource not found");
            return readResult(context, uri, () -> match.entry().handler().readAsync(context, uri, match.params()));
        }

        private Object readResult(
                DispatchContext context, String uri, Callable<CompletionStage<? extends ResourceContents>> invoker) {
            ResourceContents contents;
            try {
                contents = HandlerFutures.joinInterruptibly(invoker.call());
            } catch (Exception e) {
                logger.error("Resource handler error for '{}'", uri, e);
                return JsonRpcErrors.internalError("Resource handler failed");
            }
            return context.responseMapper().readResourceResult(List.of(contents));
        }

        private static @Nullable ReadResourceRequest toReadParams(Object params) {
            if (params instanceof ReadResourceRequestParams p) {
                return ReadResourceRequest.of(p.uri(), null);
            }
            if (params instanceof Map<?, ?> map && map.get("uri") instanceof String uri) {
                return ReadResourceRequest.of(uri, null);
            }
            return null;
        }
    }

    private record ResourcesSubscribeHandler(ResourceRegistry registry) implements RpcMethodHandler {

        @Override
        public String method() {
            return "resources/subscribe";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var uri = extractUri(params);
            if (uri == null) {
                return JsonRpcErrors.invalidRequest("Missing resource URI");
            }
            var session = context.session();
            if (session == null) {
                return JsonRpcErrors.invalidRequest("resources/subscribe requires a session");
            }
            registry.subscribe(uri, session.id());
            return context.responseMapper().emptyResult();
        }

        private static @Nullable String extractUri(Object params) {
            if (params instanceof SubscribeRequestParams p) {
                return p.uri();
            }
            if (!(params instanceof Map<?, ?> map)) {
                return null;
            }
            if (map.get("uri") instanceof String s) {
                return s;
            }
            return null;
        }
    }

    private record ResourcesUnsubscribeHandler(ResourceRegistry registry) implements RpcMethodHandler {

        @Override
        public String method() {
            return "resources/unsubscribe";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var uri = extractUri(params);
            if (uri == null) {
                return JsonRpcErrors.invalidRequest("Missing resource URI");
            }
            var session = context.session();
            if (session == null) {
                return JsonRpcErrors.invalidRequest("resources/unsubscribe requires a session");
            }
            registry.unsubscribe(uri, session.id());
            return context.responseMapper().emptyResult();
        }

        private static @Nullable String extractUri(Object params) {
            if (params instanceof UnsubscribeRequestParams p) {
                return p.uri();
            }
            if (!(params instanceof Map<?, ?> map)) {
                return null;
            }
            if (map.get("uri") instanceof String s) {
                return s;
            }
            return null;
        }
    }
}
