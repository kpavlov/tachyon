/* Copyright (c) 2026 Konstantin Pavlov and contributors. */
package dev.tachyonmcp.server.features.completions;

import java.util.List;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * Completion candidates for a single argument, per the MCP {@code completion/complete} result
 * shape. The spec caps a response at 100 values; the dispatcher truncates and forces
 * {@code hasMore=true} if a handler returns more.
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface CompletionResult {

    /**
     * Candidate values ranked by relevance.
     */
    List<String> values();

    /**
     * The total number of matches, if known.
     */
    @Nullable
    Double total();

    /**
     * Whether additional results exist beyond {@link #values()}.
     */
    @Nullable
    Boolean hasMore();

    /**
     * An empty completion result with no candidates, unknown total, and no more results.
     */
    static CompletionResult empty() {
        return DefaultCompletionResult.builder()
                .values(List.of())
                .hasMore(false)
                .build();
    }

    /**
     * Creates a result with candidate values and no total/hasMore metadata.
     */
    static CompletionResult of(List<String> values) {
        return DefaultCompletionResult.builder().values(values).build();
    }

    /**
     * Creates a result with candidate values and total/hasMore metadata.
     */
    static CompletionResult of(List<String> values, @Nullable Double total, @Nullable Boolean hasMore) {
        return DefaultCompletionResult.builder()
                .values(values)
                .total(total)
                .hasMore(hasMore)
                .build();
    }

    static Builder builder() {
        return DefaultCompletionResult.builder();
    }

    interface Builder {
        Builder values(Iterable<String> elements);

        default Builder values(String... values) {
            return values(List.of(values));
        }

        Builder total(@Nullable Double total);

        Builder hasMore(@Nullable Boolean hasMore);

        CompletionResult build();
    }
}
