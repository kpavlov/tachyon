/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tasks;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TaskDescriptorTest {

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> TaskDescriptor.of("", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectWhitespaceOnlyName() {
        assertThatThrownBy(() -> TaskDescriptor.of("  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}
