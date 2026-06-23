/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features;

import java.util.List;
import org.jspecify.annotations.Nullable;

record DefaultPaginatedResult<R>(List<R> items, @Nullable String nextCursor) implements PaginatedResult<R> {

    @Override
    public boolean hasMore() {
        return nextCursor != null;
    }
}
