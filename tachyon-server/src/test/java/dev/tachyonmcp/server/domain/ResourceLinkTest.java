/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResourceLinkTest {

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> ResourceLink.of("resource://x", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectBlankUri() {
        assertThatThrownBy(() -> ResourceLink.of("  ", "link"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uri");
    }

    @Test
    void shouldRejectNegativeSize() {
        assertThatThrownBy(() ->
                        ResourceLink.builder("resource://x", "link").size(-1.0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    void shouldRejectNaNSize() {
        assertThatThrownBy(() -> ResourceLink.builder("resource://x", "link")
                        .size(Double.NaN)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }
}
