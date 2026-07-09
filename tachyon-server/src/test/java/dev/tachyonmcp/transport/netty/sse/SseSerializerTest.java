/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty.sse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.runtime.SseEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SseSerializerTest {

    @Test
    void encodesSingleLineEvent() {
        assertThat(encode(new SseEvent("42", "response", "{\"ok\":true}")))
                .isEqualTo("id: 42\nevent: response\ndata: {\"ok\":true}\n\n");
    }

    @Test
    void encodesMultiLineDataAsSeparateDataLines() {
        assertThat(encode(new SseEvent("1", "message", "line1\nline2")))
                .isEqualTo("id: 1\nevent: message\ndata: line1\ndata: line2\n\n");
    }

    @Test
    void encodesEmptyData() {
        assertThat(encode(new SseEvent("7", "message", ""))).isEqualTo("id: 7\nevent: message\ndata: \n\n");
    }

    private static String encode(SseEvent event) {
        ByteBuf buf = SseSerializer.encode(ByteBufAllocator.DEFAULT, event);
        try {
            return buf.toString(StandardCharsets.UTF_8);
        } finally {
            buf.release();
        }
    }
}
