/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ReadResourceRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.SubscribeRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.UnsubscribeRequestParams;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.features.PaginatedResult;
import dev.tachyonmcp.server.features.tools.ToolRegistry;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** Registry for resources, templates, and subscriptions. */
public class ResourceRegistry {

    private final ConcurrentHashMap<String, ResourceEntry> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResourceEntry> byUri = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResourceTemplateEntry> templates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final Server server;

    private @Nullable Runnable onChange;

    private static final int DEFAULT_PAGE_SIZE = 50;

    /** Creates a resource registry bound to the given server (for broadcasting subscription notifications). */
    public ResourceRegistry(Server server) {
        this.server = server;
    }

    public void onChange(@Nullable Runnable callback) {
        this.onChange = callback;
    }

    private void fireOnChange() {
        if (onChange != null) {
            onChange.run();
        }
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

    /** Returns the resource descriptor by name. */
    public @Nullable ResourceDescriptor get(String name) {
        var entry = byName.get(name);
        return entry != null ? entry.descriptor() : null;
    }

    public Collection<ResourceDescriptor> getAll() {
        return byName.values().stream().map(ResourceEntry::descriptor).toList();
    }

    public PaginatedResult<ResourceDescriptor> list(int limit, @Nullable String cursor) {
        return list(limit, cursor, DEFAULT_PAGE_SIZE, descriptor -> true);
    }

    public PaginatedResult<ResourceDescriptor> list(
            int limit, @Nullable String cursor, Predicate<ResourceDescriptor> filter) {
        return list(limit, cursor, DEFAULT_PAGE_SIZE, filter);
    }

    public PaginatedResult<ResourceDescriptor> list(int limit, @Nullable String cursor, int defaultLimit) {
        return list(limit, cursor, defaultLimit, descriptor -> true);
    }

    public PaginatedResult<ResourceDescriptor> list(
            int limit, @Nullable String cursor, int defaultLimit, Predicate<ResourceDescriptor> filter) {
        if (limit <= 0) {
            limit = defaultLimit;
        }
        var all = byName.values().stream()
                .map(ResourceEntry::descriptor)
                .filter(filter)
                .sorted(Comparator.comparing(ResourceDescriptor::name))
                .toList();
        var result = new ArrayList<ResourceDescriptor>();
        boolean pastCursor = cursor == null;
        for (var item : all) {
            if (!pastCursor) {
                if (item.name().equals(cursor)) {
                    pastCursor = true;
                }
                continue;
            }
            result.add(item);
            if (result.size() >= limit) {
                break;
            }
        }
        String nextCursor = null;
        if (result.size() >= limit) {
            var lastItem = result.getLast();
            int lastIdx = all.indexOf(lastItem);
            if (lastIdx >= 0 && lastIdx < all.size() - 1) {
                nextCursor = lastItem.name();
            }
        }
        return PaginatedResult.of(result, nextCursor);
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
                    if (!matcher.matches()) return (TemplateMatch) null;
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

    /** Returns whether the given session is subscribed to the resource URI. */
    public boolean isSubscribed(String uri, String sessionId) {
        var subs = subscriptions.get(uri);
        return subs != null && subs.contains(sessionId);
    }

    /** Notifies all subscribed sessions that a resource has been updated. */
    public void notifyResourceUpdated(String uri) {
        var subscribedSessionIds = subscriptions.get(uri);
        if (subscribedSessionIds == null || subscribedSessionIds.isEmpty()) {
            return;
        }
        var paramsMap = new LinkedHashMap<String, Object>();
        paramsMap.put("uri", uri);
        for (var sessionId : subscribedSessionIds) {
            server.getSession(sessionId)
                    .ifPresent(
                            session -> server.sendNotification(session, "notifications/resources/updated", paramsMap));
        }
    }

    private record ResourcesListHandler(ResourceRegistry registry) implements RpcMethodHandler {

        @Override
        public String method() {
            return "resources/list";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var limit = ToolRegistry.parseLimit(params);
            var cursor = ToolRegistry.parseCursor(params);
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

        @Override
        public String method() {
            return "resources/read";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var readParams = toReadParams(params);
            if (readParams == null) {
                return JsonRpcErrors.invalidRequest("Missing resource URI");
            }
            var uri = readParams.uri();
            var entry = registry.getByUri(uri);
            if (entry != null) {
                var extId = entry.descriptor().extensionId();
                if (extId != null && !context.isExtensionEnabled(extId)) {
                    return JsonRpcErrors.resourceNotFound("Resource not found");
                }
            }
            if (entry == null) {
                var match = registry.matchTemplate(uri);
                if (match != null) {
                    var content = match.entry().handler().read(context, uri, match.params());
                    return context.responseMapper().readResourceResult(List.of(content));
                }
                return JsonRpcErrors.resourceNotFound("Resource not found");
            }
            var contents = entry.handler().read(context, readParams);
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
            registry.subscriptions
                    .computeIfAbsent(uri, k -> new CopyOnWriteArraySet<>())
                    .add(session.id());
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
            var subs = registry.subscriptions.get(uri);
            if (subs != null) {
                subs.remove(session.id());
                if (subs.isEmpty()) {
                    registry.subscriptions.remove(uri);
                }
            }
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
