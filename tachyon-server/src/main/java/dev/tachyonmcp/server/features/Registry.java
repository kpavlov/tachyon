/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features;

import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.McpResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** Base registry for named, paginated MCP resource types. */
public abstract class Registry<R extends McpResourceType> {

    private final ConcurrentHashMap<String, R> items = new ConcurrentHashMap<>();

    private static final int DEFAULT_PAGE_SIZE = 50;

    private @Nullable Runnable onChange;

    /** Registers a callback invoked when the registry contents change. */
    public void onChange(@Nullable Runnable callback) {
        this.onChange = callback;
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
        if (onChange != null) {
            onChange.run();
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
        if (limit <= 0) {
            limit = defaultLimit;
        }
        var all = items.values().stream()
                .filter(filter)
                .sorted(Comparator.comparing(McpResourceType::name))
                .toList();
        var result = new ArrayList<R>();
        boolean pastCursor = cursor == null;
        for (var item : all) {
            if (!pastCursor) {
                if (item.name().equals(cursor)) {
                    pastCursor = true;
                }
                continue;
            }
            result.add(item);
            if (result.size() >= limit) break;
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

    public abstract void registerHandlers(java.util.Map<String, McpMethodHandler> registry);
}
