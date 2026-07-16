/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.ServerFeature;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Abstract registry for named, paginated MCP features.
 */
@InternalApi
public abstract class AbstractRegistry<D extends ServerFeature.Descriptor, R extends ServerFeature<D>> {

    private final ConcurrentHashMap<String, R> items = new ConcurrentHashMap<>();

    private final ChangeSupport changes = new ChangeSupport();

    private final int defaultPageSize;

    protected AbstractRegistry(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    /** Returns the configured default page size. */
    protected int defaultPageSize() {
        return defaultPageSize;
    }

    /** Registers a callback invoked when the registry contents change. */
    public void onChange(Runnable callback) {
        changes.onChange(callback);
    }

    /** Adds or replaces an item by name. */
    protected void addItem(R item) {
        items.put(item.descriptor().name(), item);
        fireOnChange();
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

    /** Returns the item by name, or {@code null} if not found. */
    public @Nullable R get(String name) {
        return items.get(name);
    }

    /** Returns all registered items. */
    public Collection<R> getAll() {
        return items.values();
    }

    /**
     * Returns whether the registry is empty.
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    protected PaginatedResult<R> listItems(int limit, @Nullable String cursor) {
        int lim = limit > 0 ? limit : defaultPageSize;
        var all = getAll().stream()
                .sorted(Comparator.comparing(item -> item.descriptor().name()))
                .toList();
        return Pagination.paginate(all, lim, cursor, item -> item.descriptor().name());
    }

    public PaginatedResult<D> list(int limit, @Nullable String cursor) {
        return list(limit, cursor, (any) -> true);
    }

    public PaginatedResult<D> list(int limit, @Nullable String cursor, Predicate<D> filter) {
        int lim = limit > 0 ? limit : defaultPageSize;
        var all = getAll().stream()
                .map(ServerFeature::descriptor)
                .filter(filter)
                .sorted(Comparator.comparing(ServerFeature.Descriptor::name))
                .toList();
        return Pagination.paginate(all, lim, cursor, ServerFeature.Descriptor::name);
    }

    public abstract void registerHandlers(Map<String, RpcMethodHandler> registry);
}
