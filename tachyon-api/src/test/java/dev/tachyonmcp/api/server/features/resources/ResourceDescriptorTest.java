/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.resources;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceDescriptorTest {

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> ResourceDescriptor.of("  ", "resource://x", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectBlankUri() {
        assertThatThrownBy(() -> ResourceDescriptor.of("res", "", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uri");
    }

    @Test
    void shouldRejectNegativeSize() {
        assertThatThrownBy(() -> ResourceDescriptor.of("res", "resource://x", null, null, null, null, -1L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }
}
