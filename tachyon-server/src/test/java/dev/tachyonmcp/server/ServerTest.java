/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.runtime.Backpressure;
import dev.tachyonmcp.runtime.SessionState;
import dev.tachyonmcp.runtime.SseConnection;
import dev.tachyonmcp.runtime.SseEvent;
import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.SessionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ServerTest {

    @Test
    void createAndRemoveSession() {
        try (var server = TachyonServer.builder().build()) {
            var conn = new TestConnection();
            var session = server.createSession("sess_1");
            session.connection(conn);

            assertThat(session.state()).isEqualTo(SessionState.INITIALIZING);
            assertThat(server.getSession("sess_1")).isPresent();

            server.removeSession("sess_1");
            assertThat(server.getSession("sess_1")).isEmpty();
            assertThat(session.state()).isEqualTo(SessionState.CLOSED);
        }
    }

    @Test
    void appendResponsePersistsToChronicle() {
        try (var server = TachyonServer.builder().build()) {
            var session = server.createSession("sess_1");

            var response = new SessionEvent.ResponseEvent("sess_1", 1, "{\"ok\":true}", 1000L, -1, null);
            var sseEvent = server.appendResponse(session, response);

            assertThat(sseEvent.event()).isEqualTo("message");
            assertThat(sseEvent.data()).isEqualTo("{\"ok\":true}");

            var replayed = server.replay("sess_1", -1);
            assertThat(replayed).hasSize(1);
        }
    }

    @Test
    void replayAfterReconnect() {
        try (var server = TachyonServer.builder().build()) {
            server.createSession("sess_1");

            server.appendEvent(new SessionEvent.ResponseEvent("sess_1", 1, "{\"a\":1}", 100L, -1, null));
            server.appendEvent(new SessionEvent.ResponseEvent("sess_1", 2, "{\"b\":2}", 200L, -1, null));
            server.appendEvent(new SessionEvent.ResponseEvent("sess_1", 3, "{\"c\":3}", 300L, -1, null));

            var replayed = server.replay("sess_1", -1);
            assertThat(replayed).hasSize(3);
        }
    }

    @Test
    void backpressureReflectsConnectionState() {
        try (var server = TachyonServer.builder().build()) {
            var conn = new TestConnection();
            var session = server.createSession("sess_1");
            session.connection(conn);

            conn.writable = true;
            assertThat(server.backpressure(session)).isEqualTo(Backpressure.HOT);

            conn.writable = false;
            assertThat(server.backpressure(session)).isEqualTo(Backpressure.COLD);
        }
    }

    @Test
    void pumpChronicle() {
        try (var server = TachyonServer.builder().build()) {
            var conn = new TestConnection();
            var session = server.createSession("sess_1");
            session.connection(conn);

            server.appendEvent(new SessionEvent.ResponseEvent("sess_1", 1, "{\"a\":1}", 100L, -1, null));
            server.appendEvent(
                    new SessionEvent.NotificationEvent("sess_1", "notifications/message", "{}", 200L, -1, null));

            server.pumpChronicle(session);

            assertThat(conn.sent).hasSize(2);
        }
    }

    @Test
    void replaceExistingSession() {
        try (var server = TachyonServer.builder().build()) {
            var session1 = server.createSession("sess_1");
            var session2 = server.createSession("sess_1");

            assertThat(session1.state()).isEqualTo(SessionState.CLOSED);
            assertThat(session2.state()).isEqualTo(SessionState.INITIALIZING);
        }
    }

    @Test
    void sendRequestPersistsOutboundRequestEventForReplay() {
        try (var server = TachyonServer.builder().build()) {
            var session = server.createSession("sess_out");
            session.activate();

            server.sendRequest(session, "sampling/createMessage", Map.of("p", "v"));

            var events = server.replay("sess_out", -1);
            var outbound = events.stream()
                    .filter(e -> e instanceof SessionEvent.OutboundRequestEvent)
                    .map(e -> (SessionEvent.OutboundRequestEvent) e)
                    .toList();
            assertThat(outbound).hasSize(1);
            assertThat(outbound.getFirst().method()).isEqualTo("sampling/createMessage");
            assertThat(outbound.getFirst().sseEventId()).isGreaterThan(0L);
        }
    }

    @Test
    void toSseEventConvertsOutboundRequestEvent() {
        var event = new SessionEvent.OutboundRequestEvent(
                "s", "req-1", "sampling/createMessage", "{\"p\":\"v\"}", 100L, 7L, null);

        var sseEvent = Server.toSseEvent(event);

        assertThat(sseEvent).isNotNull();
        assertThat(sseEvent.id()).isEqualTo("7");
        assertThat(sseEvent.event()).isEqualTo("message");
        assertThat(sseEvent.data()).contains("\"method\":\"sampling/createMessage\"");
        assertThat(sseEvent.data()).contains("\"id\":\"req-1\"");
    }

    @Test
    void toSseEventReturnsNullForNonSseEvents() {
        // RequestEvent and CancelEvent have sseEventId=-1 and must return null
        // to prevent NPE in replay path (replayEvents filter fix)
        var requestEvent = new SessionEvent.RequestEvent("s", 1, "ping", "{}", 100L);
        var cancelEvent = new SessionEvent.CancelEvent("s", 1, 100L);

        assertThat(Server.toSseEvent(requestEvent)).isNull();
        assertThat(Server.toSseEvent(cancelEvent)).isNull();
    }

    @Test
    void toSseEventConvertsResponseAndNotificationEvents() {
        var response = new SessionEvent.ResponseEvent("s", 1, "{\"ok\":true}", 100L, 5L, null);
        var notification =
                new SessionEvent.NotificationEvent("s", "notifications/tools/list_changed", "{}", 100L, 7L, null);

        var ssResponse = Server.toSseEvent(response);
        assertThat(ssResponse).isNotNull();
        assertThat(ssResponse.id()).isEqualTo("5");
        assertThat(ssResponse.data()).isEqualTo("{\"ok\":true}");

        var ssNotification = Server.toSseEvent(notification);
        assertThat(ssNotification).isNotNull();
        assertThat(ssNotification.id()).isEqualTo("7");
        assertThat(ssNotification.data()).contains("notifications/tools/list_changed");
    }

    @Test
    void replayReturnsMixedEventsButToSseEventFiltersNonSse() {
        // Verifies the replay path doesn't NPE on RequestEvent/CancelEvent in the log
        try (var server = TachyonServer.builder().build()) {
            server.createSession("sess_replay");

            server.appendEvent(new SessionEvent.RequestEvent("sess_replay", 1, "ping", "{}", 100L));
            server.appendEvent(new SessionEvent.ResponseEvent("sess_replay", 1, "{\"pong\":true}", 200L, -1, null));
            server.appendEvent(new SessionEvent.CancelEvent("sess_replay", 2, 300L));

            var allEvents = server.replay("sess_replay", -1);
            assertThat(allEvents).hasSize(3);

            // All events go through toSseEvent; non-SSE must return null (not throw)
            long nonNullCount = allEvents.stream()
                    .map(Server::toSseEvent)
                    .filter(Objects::nonNull)
                    .count();
            assertThat(nonNullCount).isEqualTo(1); // only the ResponseEvent
        }
    }

    @Test
    void multipleSessions() {
        try (var server = TachyonServer.builder().build()) {
            for (int i = 0; i < 10; i++) {
                server.createSession("sess_" + i);
            }

            for (int i = 0; i < 10; i++) {
                assertThat(server.getSession("sess_" + i)).isPresent();
            }
        }
    }

    @Test
    void registerToolSendsListChangedToActiveSession() {
        try (var server = TachyonServer.builder()
                .capabilities(c -> c.toolsListChanged(true))
                .build()) {
            var conn = new TestConnection();
            var session = server.createSession("sess_test");
            session.connection(conn);
            session.activate();

            server.registerTool(ToolHandler.of(
                    builder -> builder.name("dynamic-tool")
                            .description("Dynamically registered")
                            // language=json
                            .inputSchema("{\"type\": \"object\"}"),
                    (context, args) -> ToolResult.empty()));

            var listChanged = conn.sent.stream()
                    .filter(e -> e.data().contains("notifications/tools/list_changed"))
                    .toList();
            assertThat(listChanged).isNotEmpty();
        }
    }

    @Test
    void addResourceSendsListChangedToActiveSession() {
        try (var server = TachyonServer.builder()
                .capabilities(c -> c.resourcesListChanged(true))
                .build()) {
            var conn = new TestConnection();
            var session = server.createSession("sess_test");
            session.connection(conn);
            session.activate();

            server.resources()
                    .add(
                            ResourceDescriptor.of("dyn", "test://dyn", "Dyn resource", "text/plain"),
                            (ctx, req) -> TextResourceContents.of("test://dyn", "text/plain", ""));

            var listChanged = conn.sent.stream()
                    .filter(e -> e.data().contains("notifications/resources/list_changed"))
                    .toList();
            assertThat(listChanged).isNotEmpty();
        }
    }

    @Test
    void addPromptSendsListChangedToActiveSession() {
        try (var server = TachyonServer.builder()
                .capabilities(c -> c.promptsListChanged(true))
                .build()) {
            var conn = new TestConnection();
            var session = server.createSession("sess_test");
            session.connection(conn);
            session.activate();

            server.prompts().add(PromptDescriptor.of("dyn-prompt", "Dynamic prompt"), List.of());

            var listChanged = conn.sent.stream()
                    .filter(e -> e.data().contains("notifications/prompts/list_changed"))
                    .toList();
            assertThat(listChanged).isNotEmpty();
        }
    }

    @Test
    void logRespectsLevelThreshold() {
        try (var server = TachyonServer.builder().build()) {
            var conn = new TestConnection();
            var session = server.createSession("sess_log");
            session.connection(conn);
            session.activate();

            server.setLoggingLevel(session.id(), LoggingLevel.INFO);

            // Below threshold: DEBUG should not be sent
            server.log(session, LoggingLevel.DEBUG, "test.logger", "debug msg");
            var debugEvents = conn.sent.stream()
                    .filter(e -> e.data().contains("notifications/message"))
                    .toList();
            assertThat(debugEvents).isEmpty();

            // At threshold: INFO should be sent
            server.log(session, LoggingLevel.INFO, "test.logger", "info msg");
            var infoEvents = conn.sent.stream()
                    .filter(e -> e.data().contains("notifications/message"))
                    .toList();
            assertThat(infoEvents).hasSize(1);
            assertThat(infoEvents.getFirst().data()).contains("\"level\":\"info\"");
            assertThat(infoEvents.getFirst().data()).contains("\"logger\":\"test.logger\"");

            // Above threshold: WARNING should be sent
            conn.sent.clear();
            server.log(session, LoggingLevel.WARNING, "test.logger", "warn msg");
            var warnEvents = conn.sent.stream()
                    .filter(e -> e.data().contains("notifications/message"))
                    .toList();
            assertThat(warnEvents).hasSize(1);
            assertThat(warnEvents.getFirst().data()).contains("\"level\":\"warning\"");
        }
    }

    @Test
    void listChangedNotSentToNonActiveSession() {
        try (var server = TachyonServer.builder()
                .capabilities(c -> c.toolsListChanged(true))
                .build()) {
            var conn = new TestConnection();
            var session = server.createSession("sess_init");
            session.connection(conn);

            server.registerTool(
                    ToolHandler.of(builder -> builder.name("tool-during-init"), (context, args) -> ToolResult.empty()));

            var listChanged = conn.sent.stream()
                    .filter(e -> e.data().contains("list_changed"))
                    .toList();
            assertThat(listChanged).isEmpty();
        }
    }

    @Test
    void sendNotificationShouldNotReachConnectionBeingClosed() throws InterruptedException {
        // E12: broadcastNotification calls session.connection().send() with no lock;
        // session.close() holds the write lock and calls conn.close() first.
        // The race: sender reads session.connection() → still the real conn (NOOP not yet written),
        // then calls conn.send() after conn.close() was called → write-after-close.
        // This test pins the race using latches and verifies the fix prevents it.
        var closedConnSendCount = new AtomicInteger(0);
        var closeInProgress = new CountDownLatch(1);
        var releaseClose = new CountDownLatch(1);

        var conn = new SseConnection() {
            volatile boolean closeCalled = false;

            @Override
            public boolean isWritable() {
                return true;
            }

            @Override
            public void send(SseEvent event) {
                if (closeCalled) closedConnSendCount.incrementAndGet();
            }

            @Override
            public void close() {
                closeCalled = true;
                closeInProgress.countDown();
                try {
                    releaseClose.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        try (var server = TachyonServer.builder().build()) {
            var session = server.createSession("sess_e12");
            session.connection(conn);
            session.activate();

            // Thread A: close() — acquires write lock, CAS state=CLOSED, then blocks in conn.close()
            var closer = Thread.ofVirtual().start(session::close);
            assertThat(closeInProgress.await(2, TimeUnit.SECONDS)).isTrue();

            // Thread B: sendNotification while close() holds the write lock
            // Without fix: reads session.connection() = still real conn → conn.send() called after conn.close()
            // With fix: session.send() blocks on read lock → sees CLOSED after lock → returns false
            var sender =
                    Thread.ofVirtual().start(() -> server.sendNotification(session, "notifications/e12", Map.of()));
            Thread.sleep(20); // give sender time to reach the send call

            releaseClose.countDown(); // let close() complete
            closer.join(2_000);
            sender.join(2_000);

            assertThat(closedConnSendCount.get())
                    .as("notification must not reach a connection that is being closed")
                    .isZero();
        }
    }

    private static class TestConnection implements SseConnection {

        volatile boolean writable = true;
        final ArrayList<SseEvent> sent = new ArrayList<>();

        @Override
        public boolean isWritable() {
            return writable;
        }

        @Override
        public void send(SseEvent event) {
            sent.add(event);
        }
    }
}
