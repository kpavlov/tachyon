/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.session.SseEvent;
import org.junit.jupiter.api.Test;

class SseEventTest {

    @Test
    void equality() {
        var a = new SseEvent("1", "response", "{}");
        var b = new SseEvent("1", "response", "{}");
        var c = new SseEvent("1", "event", "{}");

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }
}
