/*
 * Copyright (c) 2026 Konstantin Pavlov.
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
public interface PaginatedResult<R> {

    List<R> items();

    @Nullable
    String nextCursor();

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
