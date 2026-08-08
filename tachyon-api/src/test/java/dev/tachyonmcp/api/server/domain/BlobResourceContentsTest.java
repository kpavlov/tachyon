/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BlobResourceContentsTest {

    @Test
    void shouldAcceptEmptyBlob() {
        var contents = BlobResourceContents.of("resource://x", new byte[0], "application/octet-stream");

        assertThat(contents.blob()).isEmpty();
    }

    @Test
    void shouldRejectBlankUri() {
        assertThatThrownBy(() -> BlobResourceContents.of("  ", new byte[0], null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uri");
    }
}
