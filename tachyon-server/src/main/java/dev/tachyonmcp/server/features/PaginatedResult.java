/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features;

import java.util.List;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
/** A paginated result with an optional cursor for the next page. */
public interface PaginatedResult<R> {

    /** The items on this page. */
    List<R> items();

    /** Cursor for the next page, or {@code null} if this is the last page. */
    @Nullable
    String nextCursor();

    /** Returns {@code true} if there are more items beyond this page. */
    default boolean hasMore() {
        return nextCursor() != null;
    }

    static <R> DefaultPaginatedResult.Builder<R> builder() {
        return DefaultPaginatedResult.builder();
    }

    static <R> PaginatedResult<R> of(List<R> items, @Nullable String nextCursor) {
        return DefaultPaginatedResult.of(items, nextCursor);
    }
}
