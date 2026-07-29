/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.features.resources;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.config.Mode;
import dev.tachyonmcp.api.server.domain.InvalidArgumentException;
import dev.tachyonmcp.api.server.domain.ResourceContents;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.domain.UriTemplateValue;
import dev.tachyonmcp.api.server.features.HandlerFutures;
import dev.tachyonmcp.api.server.features.PaginatedResult;
import dev.tachyonmcp.api.server.features.resources.AsyncResourceFn;
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.api.server.features.resources.ResourceFn;
import dev.tachyonmcp.api.server.features.resources.ResourceRequest;
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.api.server.features.resources.Resources;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.codecs.ProtocolCodecUtil;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.ReadResourceRequestParams;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.SubscribeRequestParams;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.UnsubscribeRequestParams;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.config.ResourcesConfig;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.features.ChangeSupport;
import dev.tachyonmcp.core.server.features.ListRequests;
import dev.tachyonmcp.core.server.features.Pagination;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.server.json.JsonUtils;
import dev.tachyonmcp.core.server.session.DispatchContext;
import dev.tachyonmcp.core.transport.jsonrpc.JsonRpcCodec;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Registry for resources, templates, and subscriptions.
 */
@InternalApi
public class DefaultResourceRegistry implements Resources {

    private static final int MAX_RESOURCE_URI_LENGTH = 8_192;
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

    private record SyncResourceFnAdapter(ResourceFn delegate) implements AsyncResourceFn {
        @Override
        public CompletionStage<? extends ResourceContents> apply(InteractionContext context, ResourceRequest request) {
            HandlerFutures.assumeVirtualThread();
            return HandlerFutures.completedOrFailed(() -> delegate.apply(context, request));
        }
    }

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

    private static boolean isValidResourceUri(String uri) {
        if (uri.isBlank() || uri.length() > MAX_RESOURCE_URI_LENGTH) {
            return false;
        }
        try {
            new URI(uri);
            return true;
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    /**
     * Registers a resource descriptor and its handler.
     *
     * <p>If resource support is disabled, the descriptor is not registered. Registering a resource
     * with an existing name replaces the previous resource and updates URI mappings accordingly. A
     * URI is unique across resources: registering a URI already owned by a different name is rejected.
     *
     * @param descriptor the resource descriptor to register
     * @param fn the resource function
     * @return this registry
     * @throws IllegalArgumentException if the URI is already registered under a different name
     */
    @Override
    public DefaultResourceRegistry register(ResourceDescriptor descriptor, ResourceFn fn) {
        registerAsync(descriptor, new SyncResourceFnAdapter(fn));
        return this;
    }

    @Override
    public DefaultResourceRegistry registerAsync(ResourceDescriptor descriptor, AsyncResourceFn fn) {
        if (config.mode() == Mode.OFF) {
            logger.debug("Resource '{}' not registered: resources capability is OFF", descriptor.name());
            return this;
        }
        var entry = new ResourceEntry(descriptor, fn);
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
            if (previous != null) {
                final var previousUri = previous.descriptor().uri();
                if (!previousUri.equals(descriptor.uri())) {
                    newByUri.remove(previousUri);
                    dropSubscriptions(previousUri);
                }
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
            dropSubscriptions(removed.descriptor().uri());
            index = new Index(Map.copyOf(newByName), Map.copyOf(newByUri));
        } finally {
            writeLock.unlock();
        }
        fireOnChange();
        return true;
    }

    @Override
    public boolean unregisterByUri(String uri) {
        writeLock.lock();
        try {
            var current = index;
            var removed = current.byUri.get(uri);
            if (removed == null) {
                return false;
            }
            final var name = removed.descriptor().name();
            var newByName = new HashMap<>(current.byName);
            var newByUri = new HashMap<>(current.byUri);
            newByName.remove(name);
            newByUri.remove(uri);
            dropSubscriptions(uri);
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
        var entry = index.byName.get(name);
        return entry != null ? Optional.of(entry.descriptor()) : Optional.empty();
    }

    @Override
    public Optional<ResourceDescriptor> findByUri(String uri) {
        var entry = index.byUri.get(uri);
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
     * @param fn the resource function
     * @return this registry
     * @throws IllegalArgumentException if a template with the same name is already registered
     */
    @Override
    public Resources registerTemplate(ResourceTemplateDescriptor descriptor, ResourceFn fn) {
        return registerTemplateAsync(descriptor, new SyncResourceFnAdapter(fn));
    }

    @Override
    public Resources registerTemplateAsync(ResourceTemplateDescriptor descriptor, AsyncResourceFn fn) {
        if (config.mode() == Mode.OFF) {
            logger.debug("Resource template '{}' not registered: resources capability is OFF", descriptor.name());
            return this;
        }
        final var prevEntry = templates.putIfAbsent(descriptor.name(), ResourceTemplateEntry.of(descriptor, fn));
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
     * Drops any subscription-set entry for a URI that no longer names a live resource, e.g. after
     * unregistering or renaming the resource that owned it. Called under {@link #writeLock} alongside
     * the index change; {@code subscriptions} is an independent map, so this never blocks subscribe.
     */
    private void dropSubscriptions(String uri) {
        subscriptions.remove(uri);
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
            if (!paginated.cursorValid()) {
                return ServerErrors.invalidParams("Invalid cursor");
            }

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
            if (ListRequests.parseCursor(params) != null) {
                return ServerErrors.invalidParams("Invalid cursor");
            }
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

        /** Compatibility fallback for callers invoking the blocking SPI method directly. */
        @Override
        public Object handle(DispatchContext context, Object params) throws Exception {
            return HandlerFutures.joinInterruptibly(handleAsync(context, params));
        }

        /** Runs resource handlers on the server executor's virtual threads without blocking it. */
        @Override
        public CompletionStage<Object> handleAsync(DispatchContext context, Object params) {
            var parsed = parseRequest(params);
            if (parsed == null) {
                return CompletableFuture.completedFuture(ServerErrors.invalidRequest("Missing resource URI"));
            }
            var uri = parsed.uri();
            if (!isValidResourceUri(uri)) {
                return CompletableFuture.completedFuture(ServerErrors.invalidParams("Invalid resource URI"));
            }
            var entry = registry.getByUri(uri);
            if (entry != null) {
                var extId = entry.descriptor().extensionId();
                if (extId != null && !context.isExtensionEnabled(extId)) {
                    return CompletableFuture.completedFuture(ServerErrors.resourceNotFound("Resource not found"));
                }
                var request = ResourceRequest.builder()
                        .uri(uri)
                        .meta(JsonUtils.toObjectMap(parsed.meta()))
                        .build();
                return readResult(context, uri, () -> entry.fn().apply(context, request));
            }
            var match = registry.matchTemplate(uri);
            if (match == null) {
                return CompletableFuture.completedFuture(
                        ServerErrors.resourceNotFound("Resource not found", Map.of("uri", uri)));
            }
            var request = ResourceRequest.builder()
                    .uri(uri)
                    .params(match.params())
                    .uriTemplate(match.entry().descriptor().uriTemplate())
                    .meta(JsonUtils.toObjectMap(parsed.meta()))
                    .build();
            return readResult(context, uri, () -> match.entry().fn().apply(context, request));
        }

        private CompletionStage<Object> readResult(
                DispatchContext context, String uri, Callable<CompletionStage<? extends ResourceContents>> invoker) {
            // invokeAndMap: guards the synchronous-throw/null-stage cases, then re-anchors onto a
            // tachyon- virtual thread only when the handler's stage is still pending, so a
            // foreign completer thread never leaks into response mapping, without adding an
            // executor hop to the common already-resolved case.
            return HandlerFutures.invokeAndMap(
                    "Resource handler for '" + uri + "' returned a null CompletionStage",
                    invoker,
                    context.engine().executor(),
                    (contents, cause) -> {
                        if (cause != null) {
                            if (cause instanceof InvalidArgumentException e) {
                                return ServerErrors.invalidParams(
                                        "invalid argument '" + e.argName() + "': " + e.getMessage());
                            }
                            var error = ServerErrors.fromUnhandledException(cause, "Resource handler failed");
                            if (error.kind() == ServerError.Kind.INVALID_PARAMS) {
                                logger.debug("Resource handler rejected invalid params for '{}'", uri);
                            } else {
                                logger.error("Resource handler error for '{}'", uri, cause);
                            }
                            return error;
                        }
                        return context.responseMapper().readResourceResult(List.of(contents));
                    });
        }

        private static @Nullable RequestParams parseRequest(Object params) {
            if (params instanceof ReadResourceRequestParams(Map<String, JsonNode> meta, String uri)) {
                return uri == null ? null : new RequestParams(uri, meta);
            }
            if (params instanceof Map<?, ?> map) {
                var json = JsonRpcCodec.writeValueAsString(map);
                var typed = ProtocolCodecUtil.decodeWithCodec(json, ReadResourceRequestParams.class);
                return typed.uri() == null ? null : new RequestParams(typed.uri(), typed._meta());
            }
            return null;
        }

        private record RequestParams(String uri, @Nullable Map<String, JsonNode> meta) {}
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
                return ServerErrors.invalidRequest("Missing resource URI");
            }
            if (!isValidResourceUri(uri)) {
                return ServerErrors.invalidParams("Invalid resource URI");
            }
            var session = context.session();
            if (session == null) {
                return ServerErrors.invalidRequest("resources/subscribe requires a session");
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
                return ServerErrors.invalidRequest("Missing resource URI");
            }
            if (!isValidResourceUri(uri)) {
                return ServerErrors.invalidParams("Invalid resource URI");
            }
            var session = context.session();
            if (session == null) {
                return ServerErrors.invalidRequest("resources/unsubscribe requires a session");
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
