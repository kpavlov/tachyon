/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RpcDispatcherTest {

    private static RpcDispatcher.DispatchResult.Response asResponse(RpcDispatcher.DispatchResult result) {
        assertThat(result).isInstanceOf(RpcDispatcher.DispatchResult.Response.class);
        return (RpcDispatcher.DispatchResult.Response) result;
    }

    @Test
    void shouldAllowDifferentIdsInSameSession() {
        try (var server = TachyonServer.builder().build()) {
            server.createSession("sess_diff");
            var dispatcher = new RpcDispatcher(server, server.executor());

            var first = asResponse(dispatcher
                    .dispatchRequestAsync(1, "ping", null, "sess_diff")
                    .join());
            assertThat(first.responseBody().toString(StandardCharsets.UTF_8)).contains("result");

            var second = asResponse(dispatcher
                    .dispatchRequestAsync(2, "ping", null, "sess_diff")
                    .join());
            assertThat(second.responseBody().toString(StandardCharsets.UTF_8)).contains("result");
        }
    }

    @Test
    void shouldAllowSameIdInDifferentSessions() {
        try (var server = TachyonServer.builder().build()) {
            server.createSession("sess_a");
            server.createSession("sess_b");
            var dispatcher = new RpcDispatcher(server, server.executor());

            var first = asResponse(
                    dispatcher.dispatchRequestAsync(42, "ping", null, "sess_a").join());
            assertThat(first.responseBody().toString(StandardCharsets.UTF_8)).contains("result");

            var second = asResponse(
                    dispatcher.dispatchRequestAsync(42, "ping", null, "sess_b").join());
            assertThat(second.responseBody().toString(StandardCharsets.UTF_8)).contains("result");
        }
    }

    @Test
    void shouldRejectNonInitializeRequestBeforeSessionIsActive() {
        try (var server = TachyonServer.builder().session(s -> s.enabled(true)).build()) {
            server.createSession("sess_init");
            var dispatcher = new RpcDispatcher(server, server.executor());

            var result = asResponse(dispatcher
                    .dispatchRequestAsync(1, "tools/list", null, "sess_init")
                    .join());
            var body = result.responseBody().toString(StandardCharsets.UTF_8);
            assertThat(body).contains("error");
            assertThat(body).contains("-32600");
        }
    }

    @Test
    void shouldAcceptPingBeforeSessionIsActive() {
        try (var server = TachyonServer.builder().build()) {
            server.createSession("sess_ping");
            var dispatcher = new RpcDispatcher(server, server.executor());

            var result = asResponse(dispatcher
                    .dispatchRequestAsync(1, "ping", null, "sess_ping")
                    .join());
            var body = result.responseBody().toString(StandardCharsets.UTF_8);
            assertThat(body).contains("result");
            assertThat(body).doesNotContain("error");
        }
    }

    @Test
    void shouldAcceptRequestAfterSessionIsActive() {
        try (var server = TachyonServer.builder().build()) {
            var session = server.createSession("sess_active");
            session.activate();
            var dispatcher = new RpcDispatcher(server, server.executor());

            var result = asResponse(dispatcher
                    .dispatchRequestAsync(1, "ping", null, "sess_active")
                    .join());
            var body = result.responseBody().toString(StandardCharsets.UTF_8);
            assertThat(body).contains("result");
        }
    }

    @Test
    void cancelsPendingRequestWithMatchingId() {
        try (var server = TachyonServer.builder().session(s -> s.enabled(true)).build()) {
            var session = server.createSession("sess_cancel-pending");
            session.activate();
            var dispatcher = new RpcDispatcher(server, server.executor());

            var requestId = "test-req-1";
            var pending = new java.util.concurrent.CompletableFuture<String>();
            server.registerPendingRequest(requestId, pending);

            var params = Map.of("requestId", requestId, "reason", "User cancelled");
            var result = dispatcher.dispatchNotification("notifications/cancelled", params, "sess_cancel-pending");
            assertThat(result).isInstanceOf(RpcDispatcher.DispatchResult.Accepted.class);
            assertThat(pending).isCompletedExceptionally();
            assertThat(pending.exceptionNow().getMessage()).contains("Cancelled: User cancelled");
        }
    }

    @Test
    void cancelsWithoutSessionLogsAndAccepts() {
        try (var server = TachyonServer.builder().build()) {
            var dispatcher = new RpcDispatcher(server, server.executor());

            var params = Map.of("requestId", 1, "reason", "no-session");
            var result = dispatcher.dispatchNotification("notifications/cancelled", params, null);
            assertThat(result).isInstanceOf(RpcDispatcher.DispatchResult.Accepted.class);
        }
    }

    @Test
    void cancelsWithUnknownRequestIdIsAccepted() {
        try (var server = TachyonServer.builder().build()) {
            var session = server.createSession("sess_cancel-unknown");
            session.activate();
            var dispatcher = new RpcDispatcher(server, server.executor());

            var result = dispatcher.dispatchNotification(
                    "notifications/cancelled", Map.of("requestId", "missing"), "sess_cancel-unknown");
            assertThat(result).isInstanceOf(RpcDispatcher.DispatchResult.Accepted.class);
        }
    }

    @Test
    void cancelsWithEmptyParamsIsAccepted() {
        try (var server = TachyonServer.builder().build()) {
            server.createSession("sess_cancel-empty");
            var dispatcher = new RpcDispatcher(server, server.executor());

            var result = dispatcher.dispatchNotification("notifications/cancelled", Map.of(), "sess_cancel-empty");
            assertThat(result).isInstanceOf(RpcDispatcher.DispatchResult.Accepted.class);
        }
    }

    @Test
    void cancelsWithNullParamsIsAccepted() {
        try (var server = TachyonServer.builder().build()) {
            server.createSession("sess_cancel-null");
            var dispatcher = new RpcDispatcher(server, server.executor());

            var result = dispatcher.dispatchNotification("notifications/cancelled", null, "sess_cancel-null");
            assertThat(result).isInstanceOf(RpcDispatcher.DispatchResult.Accepted.class);
        }
    }

    @Test
    void shouldRejectRequestAfterSessionIsClosed() {
        try (var server = TachyonServer.builder().session(s -> s.enabled(true)).build()) {
            var session = server.createSession("sess_closed");
            session.activate();
            session.close();
            var dispatcher = new RpcDispatcher(server, server.executor());

            var result = asResponse(dispatcher
                    .dispatchRequestAsync(1, "ping", null, "sess_closed")
                    .join());
            var body = result.responseBody().toString(StandardCharsets.UTF_8);
            assertThat(body).contains("error");
            assertThat(body).contains("-32600");
        }
    }
}
