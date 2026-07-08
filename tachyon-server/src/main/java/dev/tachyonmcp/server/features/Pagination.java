/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Static pagination utility shared by registries. */
public final class Pagination {

    /** Default page size used when a request omits (or non-positively sizes) its limit. */
    public static final int DEFAULT_PAGE_SIZE = 50;

    private Pagination() {}

    /**
     * Paginates a sorted list. Items must be sorted in ascending order by the key function.
     *
     * @param sorted the sorted list of items
     * @param limit requested page size (≤0 means default)
     * @param cursor opaque cursor (last item name from previous page, or null for first page)
     * @param defaultLimit fallback page size when limit ≤ 0
     * @param key extracts the cursor key from each item
     */
    public static <T> PaginatedResult<T> paginate(
            List<T> sorted, int limit, @Nullable String cursor, int defaultLimit, Function<T, String> key) {
        if (limit <= 0) {
            limit = defaultLimit;
        }
        if (limit <= 0) {
            limit = 1;
        }
        var result = new ArrayList<T>();
        boolean pastCursor = cursor == null;
        for (var item : sorted) {
            if (!pastCursor) {
                if (key.apply(item).equals(cursor)) {
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
            int lastIdx = sorted.indexOf(lastItem);
            if (lastIdx >= 0 && lastIdx < sorted.size() - 1) {
                nextCursor = key.apply(lastItem);
            }
        }
        return PaginatedResult.of(result, nextCursor);
    }

    /**
     * Paginates with the default page size.
     *
     * @see #paginate(List, int, String, int, Function)
     */
    public static <T> PaginatedResult<T> paginate(
            List<T> sorted, int limit, @Nullable String cursor, Function<T, String> key) {
        return paginate(sorted, limit, cursor, DEFAULT_PAGE_SIZE, key);
    }
}
