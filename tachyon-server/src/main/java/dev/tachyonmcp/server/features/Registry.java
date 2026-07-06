/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features;

import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.ServerResourceType;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** Base registry for named, paginated MCP resource types. */
public abstract class Registry<R extends ServerResourceType> {

    private final ConcurrentHashMap<String, R> items = new ConcurrentHashMap<>();

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final List<Runnable> onChangeListeners = new CopyOnWriteArrayList<>();

    /** Registers a callback invoked when the registry contents change. */
    public void onChange(@Nullable Runnable callback) {
        if (callback != null) {
            onChangeListeners.add(callback);
        }
    }

    void addChangeListener(Runnable listener) {
        onChangeListeners.add(listener);
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
        for (var listener : onChangeListeners) {
            listener.run();
        }
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
        return list(limit, cursor, DEFAULT_PAGE_SIZE, r -> true);
    }

    public PaginatedResult<R> list(int limit, @Nullable String cursor, Predicate<R> filter) {
        return list(limit, cursor, DEFAULT_PAGE_SIZE, filter);
    }

    public PaginatedResult<R> list(int limit, @Nullable String cursor, int defaultLimit) {
        return list(limit, cursor, defaultLimit, r -> true);
    }

    public PaginatedResult<R> list(int limit, @Nullable String cursor, int defaultLimit, Predicate<R> filter) {
        var all = items.values().stream()
                .filter(filter)
                .sorted(Comparator.comparing(ServerResourceType::name))
                .toList();
        return Pagination.paginate(all, limit, cursor, defaultLimit, ServerResourceType::name);
    }

    public abstract void registerHandlers(Map<String, RpcMethodHandler> registry);
}
