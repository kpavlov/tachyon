/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ListRequestsTest {

    @Test
    void parseLimitFromNumber() {
        assertThat(ListRequests.parseLimit(Map.of("limit", 25))).isEqualTo(25);
    }

    @Test
    void parseLimitFromNonNumberReturnsZero() {
        assertThat(ListRequests.parseLimit(Map.of("limit", "abc"))).isZero();
    }

    @Test
    void parseLimitFromNullReturnsZero() {
        assertThat(ListRequests.parseLimit(null)).isZero();
    }

    @Test
    void parseLimitFromEmptyMapReturnsZero() {
        assertThat(ListRequests.parseLimit(Map.of())).isZero();
    }

    @Test
    void parseCursorFromString() {
        assertThat(ListRequests.parseCursor(Map.of("cursor", "abc123"))).isEqualTo("abc123");
    }

    @Test
    void parseCursorFromNonStringReturnsNull() {
        assertThat(ListRequests.parseCursor(Map.of("cursor", 42))).isNull();
    }

    @Test
    void parseCursorFromNullReturnsNull() {
        assertThat(ListRequests.parseCursor(null)).isNull();
    }

    @Test
    void parseCursorFromEmptyMapReturnsNull() {
        assertThat(ListRequests.parseCursor(Map.of())).isNull();
    }
}
