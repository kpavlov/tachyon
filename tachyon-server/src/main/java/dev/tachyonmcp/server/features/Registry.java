/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.ServerResourceType;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** Base registry for named, paginated MCP resource types. */
@InternalApi
public abstract class Registry<R extends ServerResourceType> {

    private final ConcurrentHashMap<String, R> items = new ConcurrentHashMap<>();

    private final ChangeSupport changes = new ChangeSupport();

    private final int defaultPageSize;

    protected Registry(int defaultPageSize) {
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
    public void add(R item) {
        items.put(item.name(), item);
        fireOnChange();
    }

    /** Removes the item with the given name. */
    public void remove(String name) {
        var removed = items.remove(name);
        if (removed != null) {
            fireOnChange();
        }
    }

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

    public PaginatedResult<R> list(int limit, @Nullable String cursor) {
        int lim = limit > 0 ? limit : defaultPageSize;
        var all = items.values().stream()
                .sorted(Comparator.comparing(ServerResourceType::name))
                .toList();
        return Pagination.paginate(all, lim, cursor, ServerResourceType::name);
    }

    public PaginatedResult<R> list(int limit, @Nullable String cursor, Predicate<R> filter) {
        int lim = limit > 0 ? limit : defaultPageSize;
        var all = items.values().stream()
                .filter(filter)
                .sorted(Comparator.comparing(ServerResourceType::name))
                .toList();
        return Pagination.paginate(all, lim, cursor, ServerResourceType::name);
    }

    public abstract void registerHandlers(Map<String, RpcMethodHandler> registry);
}
