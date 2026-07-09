/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.domain;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnnotationsTest {

    @Test
    void shouldAcceptPriorityAtLowerBound() {
        assertThatNoException().isThrownBy(() -> Annotations.of(null, 0.0, null));
    }

    @Test
    void shouldAcceptPriorityAtUpperBound() {
        assertThatNoException().isThrownBy(() -> Annotations.of(null, 1.0, null));
    }

    @Test
    void shouldAcceptNullPriority() {
        assertThatNoException().isThrownBy(() -> Annotations.of(null, null, null));
    }

    @Test
    void shouldRejectPriorityBelowZero() {
        assertThatThrownBy(() -> Annotations.of(null, -0.1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priority");
    }

    @Test
    void shouldRejectPriorityAboveOne() {
        assertThatThrownBy(() -> Annotations.of(null, 1.1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priority");
    }

    @Test
    void shouldRejectNaNPriority() {
        assertThatThrownBy(() -> Annotations.of(null, Double.NaN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priority");
    }
}
