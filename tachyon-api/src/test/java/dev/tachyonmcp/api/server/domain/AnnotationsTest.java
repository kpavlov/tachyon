/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.domain;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AnnotationsTest {

    @Test
    void shouldAcceptPriorityAtLowerBound() {
        assertThatNoException().isThrownBy(() -> Annotations.of(List.of(), 0.0, null));
    }

    @Test
    void shouldAcceptPriorityAtUpperBound() {
        assertThatNoException().isThrownBy(() -> Annotations.of(List.of(), 1.0, null));
    }

    @Test
    void shouldAcceptNullPriority() {
        assertThatNoException().isThrownBy(() -> Annotations.of(List.of(), null, null));
    }

    @Test
    void shouldRejectPriorityBelowZero() {
        assertThatThrownBy(() -> Annotations.of(List.of(), -0.1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priority");
    }

    @Test
    void shouldRejectPriorityAboveOne() {
        assertThatThrownBy(() -> Annotations.of(List.of(), 1.1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priority");
    }

    @Test
    void shouldRejectNaNPriority() {
        assertThatThrownBy(() -> Annotations.of(List.of(), Double.NaN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priority");
    }
}
