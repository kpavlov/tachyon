/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.prompts;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PromptDescriptorTest {

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> PromptDescriptor.of("", "A prompt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectWhitespaceOnlyName() {
        assertThatThrownBy(() -> PromptDescriptor.of("  ", "A prompt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}
