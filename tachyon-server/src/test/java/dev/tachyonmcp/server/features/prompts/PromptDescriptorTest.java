/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.prompts;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PromptDescriptorTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void shouldRejectBlankName(String name) {
        assertThatThrownBy(() -> PromptDescriptor.of(name, "A prompt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}
