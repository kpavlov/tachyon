/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.completions;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Completion candidates for a single argument, per the MCP {@code completion/complete} result
 * shape. The spec caps a response at 100 values; the dispatcher truncates and forces
 * {@code hasMore=true} if a handler returns more.
 *
 * @param values candidate values ranked by relevance
 * @param total the total number of matches, if known
 * @param hasMore whether additional results exist beyond {@code values}
 */
public record CompletionResult(
        List<String> values,
        @Nullable Double total,
        @Nullable Boolean hasMore) {

    public CompletionResult {
        values = values != null ? List.copyOf(values) : List.of();
    }

    /**
     * An empty completion result with no candidates, unknown total, and no more results.
     *
     * @return a result with an empty values list, {@code null} total, and {@code false} hasMore
     */
    public static CompletionResult empty() {
        return new CompletionResult(List.of(), null, false);
    }

    /** Creates a result with candidate values and no total/hasMore metadata. */
    public static CompletionResult of(List<String> values) {
        return new CompletionResult(values, null, null);
    }

    /** Creates a result with candidate values and total/hasMore metadata. */
    public static CompletionResult of(List<String> values, @Nullable Double total, @Nullable Boolean hasMore) {
        return new CompletionResult(values, total, hasMore);
    }
}
