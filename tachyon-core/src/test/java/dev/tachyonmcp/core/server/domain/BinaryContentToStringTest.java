/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.domain.AudioContent;
import dev.tachyonmcp.api.server.domain.BlobResourceContents;
import dev.tachyonmcp.api.server.domain.Icon;
import dev.tachyonmcp.api.server.domain.ImageContent;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class BinaryContentToStringTest {

    private static final String BINARY_PAYLOAD = "c2Vuc2l0aXZlLWJpbmFyeS1wYXlsb2Fk";
    private static final byte[] BINARY_BYTES = Base64.getDecoder().decode(BINARY_PAYLOAD);

    @Test
    void binaryResourceContainersDoNotExposePayloads() {
        var icon = Icon.of("data:image/png;base64," + BINARY_PAYLOAD, "image/png", null, null);
        var image = ImageContent.of(BINARY_BYTES, "image/png");
        var audio = AudioContent.of(BINARY_BYTES, "audio/wav");
        var blob = BlobResourceContents.of("test://blob", BINARY_BYTES, "application/octet-stream");

        assertThat(icon.toString()).doesNotContain(BINARY_PAYLOAD).contains("image/png");
        assertThat(image.toString()).doesNotContain(BINARY_PAYLOAD).contains("image/png");
        assertThat(audio.toString()).doesNotContain(BINARY_PAYLOAD).contains("audio/wav");
        assertThat(blob.toString()).doesNotContain(BINARY_PAYLOAD).contains("test://blob", "application/octet-stream");
    }
}
