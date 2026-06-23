/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface PaginatedResult<R> {

    List<R> items();

    @Nullable
    String nextCursor();

    boolean hasMore();

    static <R> PaginatedResult<R> of(List<R> items, @Nullable String nextCursor) {
        return new DefaultPaginatedResult<>(items, nextCursor);
    }
}
