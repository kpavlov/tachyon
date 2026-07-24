/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BinaryContentToStringTest {

    private static final String BINARY_PAYLOAD = "c2Vuc2l0aXZlLWJpbmFyeS1wYXlsb2Fk";

    @Test
    void binaryResourceContainersDoNotExposePayloads() {
        var icon = Icon.of("data:image/png;base64," + BINARY_PAYLOAD, "image/png", null, null);
        var image = ImageContent.of(BINARY_PAYLOAD, "image/png");
        var audio = AudioContent.of(BINARY_PAYLOAD, "audio/wav");
        var blob = BlobResourceContents.of("test://blob", BINARY_PAYLOAD, "application/octet-stream");

        assertThat(icon.toString()).doesNotContain(BINARY_PAYLOAD).contains("image/png");
        assertThat(image.toString()).doesNotContain(BINARY_PAYLOAD).contains("image/png");
        assertThat(audio.toString()).doesNotContain(BINARY_PAYLOAD).contains("audio/wav");
        assertThat(blob.toString()).doesNotContain(BINARY_PAYLOAD).contains("test://blob", "application/octet-stream");
    }
}
