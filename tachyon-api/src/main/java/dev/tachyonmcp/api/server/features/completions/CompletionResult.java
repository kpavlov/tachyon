/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.completions;

import dev.tachyonmcp.api.server.domain.HasMeta;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * Completion candidates for a single argument, per the MCP {@code completion/complete} result
 * shape. The spec caps a response at 100 values; the dispatcher truncates and forces
 * {@code hasMore=true} if a function returns more.
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface CompletionResult extends HasMeta {

    /**
     * Candidate values ranked by relevance.
     */
    List<String> values();

    /**
     * The total number of matches, if known.
     */
    @Nullable
    Long total();

    /**
     * Whether additional results exist beyond {@link #values()}.
     */
    @Nullable
    Boolean hasMore();

    /** Optional protocol extension metadata. */
    @Nullable
    @Override
    Map<String, Object> meta();

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
    static CompletionResult of(List<String> values, @Nullable Long total, @Nullable Boolean hasMore) {
        return DefaultCompletionResult.builder()
                .values(values)
                .total(total)
                .hasMore(hasMore)
                .build();
    }

    /** Creates a new builder for {@link CompletionResult}. */
    static Builder builder() {
        return DefaultCompletionResult.builder();
    }

    /** Builder for {@link CompletionResult}. */
    interface Builder {

        /** Fills this builder with the attribute values from {@code instance}. */
        Builder from(CompletionResult instance);

        /**
         * Sets the candidate values ranked by relevance.
         *
         * @param elements candidate values
         */
        Builder values(Iterable<String> elements);

        /**
         * Sets the candidate values ranked by relevance.
         *
         * @param values candidate values
         */
        default Builder values(String... values) {
            return values(List.of(values));
        }

        /**
         * Sets the total number of matches, if known.
         *
         * @param total total match count, or {@code null} if unknown
         */
        Builder total(@Nullable Long total);

        /**
         * Sets the total number of matches, if known.
         *
         * @param total total match count, or {@code null} if unknown
         */
        default Builder total(int total) {
            return total((long) total);
        }

        /**
         * Sets whether additional results exist beyond {@link #values()}.
         *
         * @param hasMore {@code true} if more results exist, or {@code null} if unknown
         */
        Builder hasMore(@Nullable Boolean hasMore);

        /**
         * Sets optional protocol extension metadata.
         *
         * @param entries metadata entries, or {@code null} for none
         */
        Builder meta(@Nullable Map<String, ?> entries);

        /** Builds the {@link CompletionResult}. */
        CompletionResult build();
    }
}
