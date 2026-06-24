/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.ContentBlockMappers;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.McpResourceMapper;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.EmptyResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListResourceTemplatesResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ListResourcesResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ReadResourceRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ReadResourceResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.SubscribeRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.UnsubscribeRequestParams;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.features.PaginatedResult;
import dev.tachyonmcp.server.features.tools.ToolRegistry;
import dev.tachyonmcp.server.session.McpContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public class ResourceRegistry {

    private final ConcurrentHashMap<String, ResourceEntry> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResourceEntry> byUri = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResourceTemplateEntry> templates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final McpServer server;

    private @Nullable Runnable onChange;

    private static final int DEFAULT_PAGE_SIZE = 50;

    public ResourceRegistry(McpServer server) {
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

    public void addTemplate(ResourceTemplateEntry template) {
        templates.put(template.name(), template);
    }

    public void removeTemplate(String name) {
        templates.remove(name);
    }

    @Nullable
    ResourceTemplateEntry resolveTemplate(String uri) {
        for (var template : templates.values()) {
            var pattern = template.uriTemplate()
                    .replace("{", "\\{")
                    .replace("}", "\\}")
                    .replace("\\{id\\}", "(.+)");
            var matcher = java.util.regex.Pattern.compile("^" + pattern + "$").matcher(uri);
            if (matcher.matches()) {
                return template;
            }
        }
        return null;
    }

    public void registerHandlers(Map<String, McpMethodHandler> registry) {
        registry.put("resources/list", new ResourcesListHandler(this));
        registry.put("resources/templates/list", new ResourcesTemplatesListHandler(this));
        registry.put("resources/read", new ResourcesReadHandler(this));
        registry.put("resources/subscribe", new ResourcesSubscribeHandler(this));
        registry.put("resources/unsubscribe", new ResourcesUnsubscribeHandler(this));
    }

    public boolean isSubscribed(String uri, String sessionId) {
        var subs = subscriptions.get(uri);
        return subs != null && subs.contains(sessionId);
    }

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

    private record ResourcesListHandler(ResourceRegistry registry) implements McpMethodHandler {

        @Override
        public String method() {
            return "resources/list";
        }

        @Override
        public Object handle(McpContext context, Object params) {
            var limit = ToolRegistry.parseLimit(params);
            var cursor = ToolRegistry.parseCursor(params);
            var paginated = registry.list(limit, cursor, e -> {
                var extId = e.extensionId();
                return extId == null || context.isExtensionEnabled(extId);
            });

            var resources = paginated.items().stream()
                    .map(McpResourceMapper::toResource)
                    .toList();
            return new ListResourcesResult(resources, null, paginated.nextCursor(), null);
        }
    }

    private record ResourcesTemplatesListHandler(ResourceRegistry registry) implements McpMethodHandler {

        @Override
        public String method() {
            return "resources/templates/list";
        }

        @Override
        public Object handle(McpContext context, Object params) {
            var models = registry.templates.values().stream()
                    .map(ResourceTemplateEntry::toModel)
                    .toList();
            return new ListResourceTemplatesResult(models, null, null, null);
        }
    }

    private record ResourcesReadHandler(ResourceRegistry registry) implements McpMethodHandler {

        @Override
        public String method() {
            return "resources/read";
        }

        @Override
        public Object handle(McpContext context, Object params) {
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
                var template = registry.resolveTemplate(uri);
                if (template != null) {
                    var re = java.util.regex.Pattern.compile(
                                    template.uriTemplate().replace("{id}", "(.+)"))
                            .matcher(uri);
                    if (re.matches()) {
                        var content = template.resolver().apply(re.group(1));
                        return new ReadResourceResult(
                                List.of(ContentBlockMappers.toProtocolResourceContents(content)), null, null);
                    }
                }
                return JsonRpcErrors.resourceNotFound("Resource not found");
            }
            var contents = entry.handler().read(context, readParams);
            return new ReadResourceResult(
                    List.of(ContentBlockMappers.toProtocolResourceContents(contents)), null, null);
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

    private record ResourcesSubscribeHandler(ResourceRegistry registry) implements McpMethodHandler {

        @Override
        public String method() {
            return "resources/subscribe";
        }

        @Override
        public Object handle(McpContext context, Object params) {
            var uri = extractUri(params);
            if (uri == null) {
                return JsonRpcErrors.invalidRequest("Missing resource URI");
            }
            registry.subscriptions
                    .computeIfAbsent(uri, k -> new CopyOnWriteArraySet<>())
                    .add(context.session().id());
            return new EmptyResult(null, null);
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

    private record ResourcesUnsubscribeHandler(ResourceRegistry registry) implements McpMethodHandler {

        @Override
        public String method() {
            return "resources/unsubscribe";
        }

        @Override
        public Object handle(McpContext context, Object params) {
            var uri = extractUri(params);
            if (uri == null) {
                return JsonRpcErrors.invalidRequest("Missing resource URI");
            }
            var subs = registry.subscriptions.get(uri);
            if (subs != null) {
                subs.remove(context.session().id());
                if (subs.isEmpty()) {
                    registry.subscriptions.remove(uri);
                }
            }
            return new EmptyResult(null, null);
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
