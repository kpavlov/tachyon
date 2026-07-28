/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.completions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompletionResultTest {

    @Test
    void emptyReturnsNoValuesNullTotalAndFalseHasMore() {
        var result = CompletionResult.empty();

        assertThat(result.values()).isEmpty();
        assertThat(result.total()).isNull();
        assertThat(result.hasMore()).isFalse();
    }
}
