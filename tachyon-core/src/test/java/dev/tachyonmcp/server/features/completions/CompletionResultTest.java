/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.completions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CompletionResultTest {

    @Test
    void emptyReturnsNoValuesNullTotalAndFalseHasMore() {
        var result = CompletionResult.empty();

        assertThat(result.values()).isEqualTo(List.of());
        assertThat(result.total()).isNull();
        assertThat(result.hasMore()).isFalse();
    }
}
