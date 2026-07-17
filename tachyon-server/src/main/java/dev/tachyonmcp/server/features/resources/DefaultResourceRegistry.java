/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ReadResourceRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.SubscribeRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.UnsubscribeRequestParams;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.config.Mode;
import dev.tachyonmcp.server.config.ResourcesConfig;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.domain.UriTemplateValue;
import dev.tachyonmcp.server.features.ChangeSupport;
import dev.tachyonmcp.server.features.HandlerFutures;
import dev.tachyonmcp.server.features.ListRequests;
import dev.tachyonmcp.server.features.PaginatedResult;
import dev.tachyonmcp.server.features.Pagination;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for resources, templates, and subscriptions.
 */
@InternalApi
public class DefaultResourceRegistry implements ResourceRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DefaultResourceRegistry.class);

    /**
     * Immutable pair of the name and URI indexes. Published atomically through {@link #index} so
     * readers always observe both maps in a mutually consistent state.
     */
    private record Index(Map<String, ResourceEntry> byName, Map<String, ResourceEntry> byUri) {
        static final Index EMPTY = new Index(Map.of(), Map.of());
    }

    private volatile Index index = Index.EMPTY;
    private final ReentrantLock writeLock = new ReentrantLock();

    private final ConcurrentHashMap<String, ResourceTemplateEntry> templates = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final ServerEngine server;
    private final ResourcesConfig config;

    private final ChangeSupport changes = new ChangeSupport();

    /**
     * Creates a resource registry bound to the given server (for broadcasting subscription notifications).
     */
    public DefaultResourceRegistry(ServerEngine server, ResourcesConfig config) {
        this.server = server;
        this.config = config;
    }

    public void onChange(Runnable callback) {
        changes.onChange(callback);
    }

    /**
     * Notifies registered callbacks that the registry has changed.
     */
    private void fireOnChange() {
        changes.fireOnChange();
    }

    /**
     * Registers a resource descriptor and its handler.
     *
     * <p>If resource support is disabled, the descriptor is not registered. Registering a resource
     * with an existing name replaces the previous resource and updates URI mappings accordingly. A
     * URI is unique across resources: registering a URI already owned by a different name is rejected.
     *
     * @param descriptor the resource descriptor to register
     * @param handler the handler used to read the resource
     * @return this registry
     * @throws IllegalArgumentException if the URI is already registered under a different name
     */
    @Override
    public DefaultResourceRegistry register(ResourceDescriptor descriptor, ResourceHandler handler) {
        if (config.mode() == Mode.OFF) {
            logger.debug("Resource '{}' not registered: resources capability is OFF", descriptor.name());
            return this;
        }
        var entry = new ResourceEntry(descriptor, handler);
        writeLock.lock();
        try {
            var current = index;
            var uriOwner = current.byUri().get(descriptor.uri());
            if (uriOwner != null && !uriOwner.descriptor().name().equals(descriptor.name())) {
                throw new IllegalArgumentException("Resource URI '" + descriptor.uri()
                        + "' is already registered under name '"
                        + uriOwner.descriptor().name() + "'");
            }
            var previous = current.byName().get(descriptor.name());
            if (entry.equals(previous)) {
                return this;
            }
            var newByName = new HashMap<>(current.byName());
            var newByUri = new HashMap<>(current.byUri());
            newByName.put(descriptor.name(), entry);
            if (previous != null && !previous.descriptor().uri().equals(descriptor.uri())) {
                newByUri.remove(previous.descriptor().uri());
            }
            newByUri.put(descriptor.uri(), entry);
            index = new Index(Map.copyOf(newByName), Map.copyOf(newByUri));
        } finally {
            writeLock.unlock();
        }
        fireOnChange();
        return this;
    }

    /**
     * Removes the resource registered under the specified name.
     *
     * @param name the resource name
     * @return {@code true} if a resource was removed, {@code false} if no resource was registered under the name
     */
    @Override
    public boolean unregister(String name) {
        writeLock.lock();
        try {
            var current = index;
            var removed = current.byName().get(name);
            if (removed == null) {
                return false;
            }
            var newByName = new HashMap<>(current.byName());
            var newByUri = new HashMap<>(current.byUri());
            newByName.remove(name);
            newByUri.remove(removed.descriptor().uri());
            index = new Index(Map.copyOf(newByName), Map.copyOf(newByUri));
        } finally {
            writeLock.unlock();
        }
        fireOnChange();
        return true;
    }

    /**
     * Finds a registered resource by name.
     *
     * @param name the resource name
     * @return the resource descriptor, or an empty optional if no resource has the specified name
     */
    @Override
    public Optional<ResourceDescriptor> find(String name) {
        var entry = index.byName().get(name);
        return entry != null ? Optional.of(entry.descriptor()) : Optional.empty();
    }

    /**
     * Lists registered resource descriptors in ascending name order.
     *
     * @return the registered resource descriptors
     */
    @Override
    public List<ResourceDescriptor> descriptors() {
        return index.byName().values().stream()
                .map(ResourceEntry::descriptor)
                .sorted(Comparator.comparing(ResourceDescriptor::name))
                .toList();
    }

    /**
     * Lists registered resources in name order using cursor-based pagination.
     *
     * @param limit  the maximum number of resources to include; the configured page size is used when this value is not positive
     * @param cursor the cursor identifying the starting position, or {@code null} to start from the beginning
     * @return      the paginated resources and the cursor for the next page
     */
    public PaginatedResult<ResourceDescriptor> list(int limit, @Nullable String cursor) {
        int lim = limit > 0 ? limit : config.pageSize();
        var all = index.byName().values().stream()
                .map(ResourceEntry::descriptor)
                .sorted(Comparator.comparing(ResourceDescriptor::name))
                .toList();
        return Pagination.paginate(all, lim, cursor, ResourceDescriptor::name);
    }

    public PaginatedResult<ResourceDescriptor> list(
            int limit, @Nullable String cursor, Predicate<ResourceDescriptor> filter) {
        int lim = limit > 0 ? limit : config.pageSize();
        var all = index.byName().values().stream()
                .map(ResourceEntry::descriptor)
                .filter(filter)
                .sorted(Comparator.comparing(ResourceDescriptor::name))
                .toList();
        return Pagination.paginate(all, lim, cursor, ResourceDescriptor::name);
    }

    /**
     * Finds the resource registered for the specified URI.
     *
     * @param uri the resource URI
     * @return the matching resource entry, or {@code null} if no resource is registered for the URI
     */
    @Nullable
    ResourceEntry getByUri(String uri) {
        return index.byUri().get(uri);
    }

    /**
     * Registers a resource template with its handler.
     *
     * <p>If resource support is disabled, the template is not registered. An exception is thrown when
     * a template with the same name already exists.
     *
     * @param descriptor the resource template descriptor
     * @param handler the handler used to process requests matching the template
     * @return this registry
     * @throws IllegalArgumentException if a template with the same name is already registered
     */
    @Override
    public ResourceRegistry registerTemplate(ResourceTemplateDescriptor descriptor, ResourceHandler handler) {
        if (config.mode() == Mode.OFF) {
            logger.debug("Resource template '{}' not registered: resources capability is OFF", descriptor.name());
            return this;
        }
        final var prevEntry = templates.putIfAbsent(descriptor.name(), ResourceTemplateEntry.of(descriptor, handler));
        if (prevEntry != null) {
            throw new IllegalArgumentException("Resource template '" + descriptor.name() + "' already exists");
        }
        fireOnChange();
        return this;
    }

    /**
     * Removes a registered resource template by name.
     *
     * @param name the name of the template to remove
     * @return {@code true} if a template was removed, {@code false} otherwise
     */
    @Override
    public boolean unregisterTemplate(String name) {
        var removed = templates.remove(name);
        if (removed != null) {
            fireOnChange();
            return true;
        }
        return false;
    }

    /**
     * Finds a registered resource template by name.
     *
     * @param name the template name
     * @return the matching resource template descriptor, or an empty optional if no template is registered with that name
     */
    @Override
    public Optional<ResourceTemplateDescriptor> findTemplate(String name) {
        var template = templates.get(name);
        return template != null ? Optional.of(template.descriptor()) : Optional.empty();
    }

    /**
     * Lists registered resource template descriptors in name order.
     *
     * @return the registered resource template descriptors sorted by name
     */
    @Override
    public List<ResourceTemplateDescriptor> templateDescriptors() {
        return templates.values().stream()
                .map(ResourceTemplateEntry::descriptor)
                .sorted(Comparator.comparing(ResourceTemplateDescriptor::name))
                .toList();
    }

    /**
     * Determines whether the registry contains no resources.
     *
     * @return {@code true} if the registry contains no resources, {@code false} otherwise
     */
    public boolean isEmpty() {
        return index.byName().isEmpty();
    }

    private record TemplateMatch(ResourceTemplateEntry entry, Map<String, UriTemplateValue> params) {}

    /**
     * Finds the most specific registered resource template matching the URI.
     *
     * @param uri the resource URI to match
     * @return the matching template and extracted parameters, or {@code null} if no template matches
     */
    @Nullable
    private TemplateMatch matchTemplate(String uri) {
        return templates.values().stream()
                .sorted(Comparator.comparingInt((ResourceTemplateEntry t) -> -UriTemplatePatterns.EXPRESSION
                                .matcher(t.descriptor().uriTemplate())
                                .replaceAll("")
                                .length())
                        .thenComparing(it -> it.descriptor().name()))
                .map(template -> {
                    try {
                        return new TemplateMatch(
                                template, template.uriTemplate().parse(uri));
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
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
    @Override
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

    private record ResourcesListHandler(DefaultResourceRegistry registry) implements RpcMethodHandler {

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

    private record ResourcesTemplatesListHandler(DefaultResourceRegistry registry) implements RpcMethodHandler {

        @Override
        public String method() {
            return "resources/templates/list";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            var templates = registry.templates.values().stream()
                    .map(ResourceTemplateEntry::descriptor)
                    .toList();
            return context.responseMapper().listResourceTemplatesResult(templates, null);
        }
    }

    private record ResourcesReadHandler(DefaultResourceRegistry registry) implements RpcMethodHandler {

        private static final Logger logger = LoggerFactory.getLogger(ResourcesReadHandler.class);

        @Override
        public String method() {
            return "resources/read";
        }

        /** Runs resource handlers on the server executor's virtual threads. */
        @Override
        public Object handle(DispatchContext context, Object params) throws Exception {
            var uri = toRawUri(params);
            if (uri == null) return JsonRpcErrors.invalidRequest("Missing resource URI");
            var entry = registry.getByUri(uri);
            if (entry != null) {
                var extId = entry.descriptor().extensionId();
                if (extId != null && !context.isExtensionEnabled(extId)) {
                    return JsonRpcErrors.resourceNotFound("Resource not found");
                }
                return readResult(context, uri, () -> entry.handler().readAsync(context, uri, Map.of(), null));
            }
            var match = registry.matchTemplate(uri);
            if (match == null) return JsonRpcErrors.resourceNotFound("Resource not found");
            return readResult(
                    context,
                    uri,
                    () -> match.entry()
                            .handler()
                            .readAsync(
                                    context,
                                    uri,
                                    match.params(),
                                    match.entry().descriptor().uriTemplate()));
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

        private static @Nullable String toRawUri(Object params) {
            if (params instanceof ReadResourceRequestParams p) {
                return p.uri();
            }
            if (params instanceof Map<?, ?> map && map.get("uri") instanceof String uri) {
                return uri;
            }
            return null;
        }
    }

    private record ResourcesSubscribeHandler(DefaultResourceRegistry registry) implements RpcMethodHandler {

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

    private record ResourcesUnsubscribeHandler(DefaultResourceRegistry registry) implements RpcMethodHandler {

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
