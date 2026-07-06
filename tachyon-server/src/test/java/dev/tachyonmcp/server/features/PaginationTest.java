/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PaginationTest {

    private static final Function<String, String> KEY = Function.identity();

    @Test
    void cursorIsLastItemReturnsNoNextCursor() {
        var items = List.of("a", "b", "c");
        var result = Pagination.paginate(items, 10, "c", KEY);
        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void cursorNotFoundReturnsEmptyPage() {
        var items = List.of("a", "b", "c");
        var result = Pagination.paginate(items, 10, "z", KEY);
        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void limitZeroOrLessUsesDefaultLimit() {
        var items = IntStream.range(0, 55).mapToObj(String::valueOf).toList();
        var zeroResult = Pagination.paginate(items, 0, null, 50, KEY);
        var negResult = Pagination.paginate(items, -1, null, 50, KEY);
        assertThat(zeroResult.items()).hasSize(50);
        assertThat(negResult.items()).hasSize(50);
    }

    @Test
    void nullCursorReturnsFirstPage() {
        var items = List.of("a", "b", "c", "d", "e");
        var result = Pagination.paginate(items, 3, null, KEY);
        assertThat(result.items()).containsExactly("a", "b", "c");
        assertThat(result.nextCursor()).isEqualTo("c");
    }

    @Test
    void fewerItemsThanLimitReturnsNoNextCursor() {
        var items = List.of("a", "b", "c");
        var result = Pagination.paginate(items, 10, null, KEY);
        assertThat(result.items()).containsExactly("a", "b", "c");
        assertThat(result.nextCursor()).isNull();
    }
}
