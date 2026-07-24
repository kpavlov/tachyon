/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.RequestId;
import org.junit.jupiter.api.Test;

class McpSessionEventTest {

    @Test
    void requestEvent() {
        var event = new SessionEvent.RequestEvent("sess_1", RequestId.of(1), "tools/list", "{}", 1000L);

        assertThat(event.sessionId()).isEqualTo("sess_1");
        assertThat(event.requestId()).isEqualTo(RequestId.of(1));
        assertThat(event.method()).isEqualTo("tools/list");
        assertThat(event.paramsJson()).isEqualTo("{}");
        assertThat(event.timestamp()).isEqualTo(1000L);
    }

    @Test
    void responseEvent() {
        var event = new SessionEvent.ResponseEvent("sess_1", RequestId.of(1), "{\"result\":\"ok\"}", 2000L, -1, null);

        assertThat(event.sessionId()).isEqualTo("sess_1");
        assertThat(event.requestId()).isEqualTo(RequestId.of(1));
        assertThat(event.resultJson()).isEqualTo("{\"result\":\"ok\"}");
        assertThat(event.timestamp()).isEqualTo(2000L);
        assertThat(event.streamKey()).isNull();
    }

    @Test
    void cancelEvent() {
        var event = new SessionEvent.CancelEvent("sess_1", RequestId.of(42), 3000L);

        assertThat(event.sessionId()).isEqualTo("sess_1");
        assertThat(event.requestId()).isEqualTo(RequestId.of(42));
        assertThat(event.timestamp()).isEqualTo(3000L);
    }

    @Test
    void outboundRequestEvent() {
        var event = new SessionEvent.OutboundRequestEvent(
                "sess_1", RequestId.of("uuid-123"), "sampling/createMessage", "{\"prompt\":\"hi\"}", 5000L, 42L, "17");

        assertThat(event.sessionId()).isEqualTo("sess_1");
        assertThat(event.requestId()).isEqualTo(RequestId.of("uuid-123"));
        assertThat(event.method()).isEqualTo("sampling/createMessage");
        assertThat(event.paramsJson()).isEqualTo("{\"prompt\":\"hi\"}");
        assertThat(event.timestamp()).isEqualTo(5000L);
        assertThat(event.sseEventId()).isEqualTo(42L);
        assertThat(event.streamKey()).isEqualTo("17");
    }

    @Test
    void allEventsAreSealed() {
        var request = new SessionEvent.RequestEvent("a", RequestId.of(1), "m", "{}", 1L);
        var response = new SessionEvent.ResponseEvent("a", RequestId.of(2), "{}", 2L, -1, null);
        var cancel = new SessionEvent.CancelEvent("a", RequestId.of(3), 3L);
        var outbound = new SessionEvent.OutboundRequestEvent("a", RequestId.of(5), "m", "{}", 5L, 5L, null);

        assertThat(request).isInstanceOf(SessionEvent.class);
        assertThat(response).isInstanceOf(SessionEvent.class);
        assertThat(cancel).isInstanceOf(SessionEvent.class);
        assertThat(outbound).isInstanceOf(SessionEvent.class);
    }
}
