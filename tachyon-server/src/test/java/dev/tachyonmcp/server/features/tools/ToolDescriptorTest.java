/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ToolDescriptorTest {

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> ToolDescriptor.builder("").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectWhitespaceOnlyName() {
        assertThatThrownBy(() -> ToolDescriptor.builder("   ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}
