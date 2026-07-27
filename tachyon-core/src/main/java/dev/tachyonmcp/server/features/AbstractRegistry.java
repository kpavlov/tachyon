/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features;

import dev.tachyonmcp.protocol.api.annotations.InternalApi;
import dev.tachyonmcp.protocol.api.server.ServerFeature;
import dev.tachyonmcp.protocol.api.server.features.PaginatedResult;
import dev.tachyonmcp.server.RpcMethodHandler;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Abstract registry for named, paginated MCP features.
 *
 * @param <D> the descriptor type for this feature
 * @param <R> the feature type registered in this registry
 */
@InternalApi
public abstract class AbstractRegistry<D extends ServerFeature.Descriptor, R extends ServerFeature<D>> {

    private final ConcurrentHashMap<String, R> items = new ConcurrentHashMap<>();

    private final ChangeSupport changes = new ChangeSupport();

    private final int defaultPageSize;

    /**
     * Creates a registry with the given default page size.
     *
     * @param defaultPageSize the default page size for paginated queries
     */
    protected AbstractRegistry(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    /**
     * Returns the configured default page size.
     *
     * @return the default page size
     */
    protected int defaultPageSize() {
        return defaultPageSize;
    }

    /**
     * Registers a callback invoked when the registry contents change.
     *
     * @param callback the change listener
     */
    public void onChange(Runnable callback) {
        changes.onChange(callback);
    }

    /**
     * Adds or replaces an item by name.
     *
     * @param item the feature to add
     */
    protected void addItem(R item) {
        items.put(item.descriptor().name(), item);
        fireOnChange();
    }

    /**
     * Adds the item if none is registered under the same name; returns {@code false} if one already exists.
     *
     * @param item the feature to add
     * @return {@code true} if added, {@code false} if an item with that name already exists
     */
    protected boolean addItemIfAbsent(R item) {
        var previous = items.putIfAbsent(item.descriptor().name(), item);
        if (previous == null) {
            fireOnChange();
            return true;
        }
        return false;
    }

    /**
     * Removes the item with the specified name and notifies change listeners when an item is removed.
     *
     * @param name the name of the item to remove
     * @return {@code true} if an item was removed, {@code false} if no item matched the name
     */
    protected boolean removeItem(String name) {
        var removed = items.remove(name);
        if (removed != null) {
            fireOnChange();
            return true;
        }
        return false;
    }

    /**
     * Notifies registered listeners that the registry contents have changed.
     */
    protected void fireOnChange() {
        changes.fireOnChange();
    }

    /**
     * Returns the item by name, or {@code null} if not found.
     *
     * @param name the item name
     * @return the item, or {@code null}
     */
    public @Nullable R get(String name) {
        return items.get(name);
    }

    /**
     * Returns all registered items.
     *
     * @return all items
     */
    public Collection<R> getAll() {
        return items.values();
    }

    /**
     * Returns whether the registry is empty.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Lists items with pagination, returning feature instances.
     *
     * @param limit  max results (0=default)
     * @param cursor pagination cursor, or {@code null}
     * @return paginated results
     */
    protected PaginatedResult<R> listItems(int limit, @Nullable String cursor) {
        int lim = limit > 0 ? limit : defaultPageSize;
        var all = getAll().stream()
                .sorted(Comparator.comparing(item -> item.descriptor().name()))
                .toList();
        return Pagination.paginate(all, lim, cursor, item -> item.descriptor().name());
    }

    /**
     * Lists all descriptors with pagination.
     *
     * @param limit  max results (0=default)
     * @param cursor pagination cursor, or {@code null}
     * @return paginated descriptors
     */
    public PaginatedResult<D> list(int limit, @Nullable String cursor) {
        return list(limit, cursor, (any) -> true);
    }

    /**
     * Lists descriptors with pagination and filter.
     *
     * @param limit  max results (0=default)
     * @param cursor pagination cursor, or {@code null}
     * @param filter descriptor filter predicate
     * @return paginated descriptors
     */
    public PaginatedResult<D> list(int limit, @Nullable String cursor, Predicate<D> filter) {
        int lim = limit > 0 ? limit : defaultPageSize;
        var all = getAll().stream()
                .map(ServerFeature::descriptor)
                .filter(filter)
                .sorted(Comparator.comparing(ServerFeature.Descriptor::name))
                .toList();
        return Pagination.paginate(all, lim, cursor, ServerFeature.Descriptor::name);
    }

    /**
     * Registers all handlers from this registry into the given handler map.
     *
     * @param registry the handler registry to populate
     */
    public abstract void registerHandlers(Map<String, RpcMethodHandler> registry);
}
