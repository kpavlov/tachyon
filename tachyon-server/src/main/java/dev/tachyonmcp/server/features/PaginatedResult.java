/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record PaginatedResult<R>(List<R> items, @Nullable String nextCursor) {

    public boolean hasMore() {
        return nextCursor != null;
    }
}
