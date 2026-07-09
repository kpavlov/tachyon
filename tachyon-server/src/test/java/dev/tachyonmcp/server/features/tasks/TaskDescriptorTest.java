/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tasks;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TaskDescriptorTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void shouldRejectBlankName(String name) {
        assertThatThrownBy(() -> TaskDescriptor.builder(name).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}
