/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty.sse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.TachyonMcpServer;
import dev.tachyonmcp.server.session.SessionEvent;
import dev.tachyonmcp.server.session.SseConnection;
import dev.tachyonmcp.server.session.SseEvent;
import java.util.ArrayList;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

class SseManagerTest {

    @Test
    void replayWithLastEventIdReturnsOnlyNewerEvents() {
        try (var server = TachyonMcpServer.builder().build()) {
            var conn = new TrackingConnection();
            var session = server.createSession("sess_replay");
            session.connection(conn);
            for (long id = 1; id <= 5; id++) {
                server.appendEvent(
                        new SessionEvent.ResponseEvent("sess_replay", id, "{\"n\":" + id + "}", 1000L + id, id));
            }

            var manager = new SseManager(server);
            manager.replayEvents(session, "3");

            assertThat(conn.sent).hasSize(2);
            assertThat(conn.sent.get(0).id()).isEqualTo("4");
            assertThat(conn.sent.get(1).id()).isEqualTo("5");
        }
    }

    @Test
    void replayWithInvalidLastEventIdSkipsReplay() {
        try (var server = TachyonMcpServer.builder().build()) {
            var conn = new TrackingConnection();
            var session = server.createSession("sess_bad");
            session.connection(conn);
            server.appendEvent(new SessionEvent.ResponseEvent("sess_bad", 1, "{}", 1000L, 1L));

            var manager = new SseManager(server);
            manager.replayEvents(session, "not-a-number");

            assertThat(conn.sent).isEmpty();
        }
    }

    @Test
    void replayWithFutureLastEventIdSkipsAll() {
        try (var server = TachyonMcpServer.builder().build()) {
            var conn = new TrackingConnection();
            var session = server.createSession("sess_future");
            session.connection(conn);
            for (long id = 1; id <= 3; id++) {
                server.appendEvent(new SessionEvent.ResponseEvent("sess_future", id, "{}", 1000L + id, id));
            }

            var manager = new SseManager(server);
            manager.replayEvents(session, "9999");

            assertThat(conn.sent).isEmpty();
        }
    }

    @Test
    void replayWithZeroLastEventIdReturnsAllSseEvents() {
        try (var server = TachyonMcpServer.builder().build()) {
            var conn = new TrackingConnection();
            var session = server.createSession("sess_zero");
            session.connection(conn);
            for (long id = 1; id <= 3; id++) {
                server.appendEvent(new SessionEvent.ResponseEvent("sess_zero", id, "{}", 1000L + id, id));
            }

            var manager = new SseManager(server);
            manager.replayEvents(session, "0");

            assertThat(conn.sent).hasSize(3);
            assertThat(conn.sent.get(0).id()).isEqualTo("1");
            assertThat(conn.sent.get(1).id()).isEqualTo("2");
            assertThat(conn.sent.get(2).id()).isEqualTo("3");
        }
    }

    @Test
    void replayMixedEventTypesSkipsNonSseEvents() {
        try (var server = TachyonMcpServer.builder().build()) {
            var conn = new TrackingConnection();
            var session = server.createSession("sess_mixed");
            session.connection(conn);
            server.appendEvent(new SessionEvent.RequestEvent("sess_mixed", 1, "ping", "{}", 1000L));
            server.appendEvent(new SessionEvent.ResponseEvent("sess_mixed", 1, "{\"pong\":true}", 1100L, 1L));
            server.appendEvent(new SessionEvent.CancelEvent("sess_mixed", 2, 1200L));
            server.appendEvent(new SessionEvent.NotificationEvent("sess_mixed", "notifications/test", "{}", 1300L, 2L));

            var manager = new SseManager(server);
            manager.replayEvents(session, "0");

            assertThat(conn.sent).hasSize(2);
            assertThat(conn.sent.get(0).id()).isEqualTo("1");
            assertThat(conn.sent.get(0).data()).isEqualTo("{\"pong\":true}");
            assertThat(conn.sent.get(1).id()).isEqualTo("2");
            assertThat(conn.sent.get(1).data()).contains("notifications/test");
        }
    }

    private static class TrackingConnection implements SseConnection {

        final ArrayList<SseEvent> sent = new ArrayList<>();

        @Override
        public boolean isWritable() {
            return true;
        }

        @Override
        public void send(@NonNull SseEvent event) {
            sent.add(event);
        }
    }
}
