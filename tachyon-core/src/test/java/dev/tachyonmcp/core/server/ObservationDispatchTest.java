/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server;

import static dev.tachyonmcp.core.test.TestUtils.newEngine;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.domain.RequestId;
import dev.tachyonmcp.api.server.features.tools.AsyncToolFn;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.server.observability.ObservationListener;
import dev.tachyonmcp.core.server.observability.ObservationScope;
import dev.tachyonmcp.core.server.observability.OperationInfo;
import dev.tachyonmcp.core.server.observability.OperationOutcome;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Behavior of the observation lifecycle wired into {@link McpDispatcher}: the two-call
 * start/complete model, per-operation outcomes, fault isolation, and — most importantly — that a
 * registered listener never forces an async handler onto a blocking join (the defect this design
 * replaces from the interceptor-based PR #288 approach).
 */
class ObservationDispatchTest {

    /** Records every {@code start}/{@code complete} call; each {@code start} returns a fresh, independently trackable scope. */
    private static final class RecordingListener implements ObservationListener {
        record StartCall(OperationInfo info) {}

        record CompleteCall(OperationInfo info, OperationOutcome outcome) {}

        final List<StartCall> starts = new CopyOnWriteArrayList<>();
        final List<CompleteCall> completions = new CopyOnWriteArrayList<>();
        volatile RuntimeException throwOnStart;
        volatile RuntimeException throwOnComplete;

        @Override
        public ObservationScope start(OperationInfo info) {
            starts.add(new StartCall(info));
            if (throwOnStart != null) throw throwOnStart;
            return ObservationScope.NOOP;
        }

        @Override
        public void complete(OperationInfo info, OperationOutcome outcome) {
            completions.add(new CompleteCall(info, outcome));
            if (throwOnComplete != null) throw throwOnComplete;
        }
    }

    private static McpDispatcher.DispatchResult.Response asResponse(McpDispatcher.DispatchResult result) {
        assertThat(result).isInstanceOf(McpDispatcher.DispatchResult.Response.class);
        return (McpDispatcher.DispatchResult.Response) result;
    }

    @Test
    void asyncHandlerNeverBlockedByObservation() throws Exception {
        var reattachClosed = new CompletableFuture<Void>();
        ObservationListener listener = new ObservationListener() {
            @Override
            public ObservationScope start(OperationInfo info) {
                return new ObservationScope() {
                    @Override
                    public void close() {}

                    @Override
                    public ObservationScope reattach() {
                        return new ObservationScope() {
                            @Override
                            public void close() {
                                reattachClosed.complete(null);
                            }

                            @Override
                            public ObservationScope reattach() {
                                return this;
                            }
                        };
                    }
                };
            }

            @Override
            public void complete(OperationInfo info, OperationOutcome outcome) {}
        };

        var gate = new CompletableFuture<ToolResult>();
        AsyncToolFn fn = (ctx, request) -> gate;
        var descriptor =
                ToolDescriptor.builder().name("gated-tool").description("gated").build();

        try (ServerEngine server = newEngine(
                b -> b.observability(o -> o.listener(listener)), s -> s.tools().registerAsync(descriptor, fn))) {
            var session = server.createSession("sess-gate");
            session.activate();
            var dispatcher = new McpDispatcher(server, server.executor());
            var params = Map.of("name", "gated-tool", "arguments", Map.of());
            var future = dispatcher.dispatchRequestAsync(RequestId.of(1), "tools/call", params, "sess-gate");

            // The scope wrapping decode + handler kickoff must close promptly, proving the
            // dispatcher never joined the still-pending handler stage to get there.
            reattachClosed.get(5, TimeUnit.SECONDS);
            assertThat(future).as("dispatch stays pending until the handler resolves").isNotDone();

            gate.complete(ToolResult.text("ok"));
            var result = asResponse(future.get(10, TimeUnit.SECONDS));
            assertThat(result.responseBodyString()).contains("ok");
        }
    }

    @Test
    void ordinaryRequestStartsThenCompletesExactlyOnceAsCompleted() {
        var listener = new RecordingListener();
        try (ServerEngine server = newEngine(b -> b.observability(o -> o.listener(listener)))) {
            server.createSession("sess-obs").activate();
            var dispatcher = new McpDispatcher(server, server.executor());

            dispatcher.dispatchRequestAsync(RequestId.of(1), "ping", null, "sess-obs").join();

            assertThat(listener.starts).hasSize(1);
            assertThat(listener.starts.getFirst().info().method()).isEqualTo("ping");
            assertThat(listener.completions).hasSize(1);
            assertThat(listener.completions.getFirst().outcome()).isInstanceOf(OperationOutcome.Completed.class);
        }
    }

    @Test
    void rejectionBeforeHandlerReportsRejectedOutcome() {
        var listener = new RecordingListener();
        try (ServerEngine server = (ServerEngine) TachyonServer.builder()
                .session(s -> s.enabled(true))
                .observability(o -> o.listener(listener))
                .build()) {
            server.createSession("sess-reject").activate();
            var dispatcher = new McpDispatcher(server, server.executor());

            var result = asResponse(dispatcher
                    .dispatchRequestAsync(RequestId.of(1), "definitely/unknown", null, "sess-reject")
                    .join());
            assertThat(result.responseBodyString()).contains("-32601");

            assertThat(listener.completions).hasSize(1);
            assertThat(listener.completions.getFirst().outcome()).isInstanceOf(OperationOutcome.Rejected.class);
        }
    }

    @Test
    void missingSessionHeaderReportsRejectedOutcomeWithoutServerError() {
        var listener = new RecordingListener();
        try (ServerEngine server = (ServerEngine) TachyonServer.builder()
                .session(s -> s.enabled(true))
                .observability(o -> o.listener(listener))
                .build()) {
            var dispatcher = new McpDispatcher(server, server.executor());

            // Not "ping" -- ping bypasses the session-header check even without a session.
            var result = dispatcher.dispatchRequestAsync(RequestId.of(1), "tools/list", null, null);
            assertThat(result.join()).isInstanceOf(McpDispatcher.DispatchResult.Status.class);

            assertThat(listener.completions).hasSize(1);
            var outcome = (OperationOutcome.Rejected) listener.completions.getFirst().outcome();
            assertThat(outcome.error()).isNull();
            assertThat(outcome.httpStatus()).isEqualTo(400);
        }
    }

    @Test
    void notificationsInitializedReportsAcceptedAndUnknownReportsIgnored() {
        var listener = new RecordingListener();
        try (ServerEngine server = (ServerEngine) TachyonServer.builder()
                .session(s -> s.enabled(true))
                .observability(o -> o.listener(listener))
                .build()) {
            server.createSession("sess-notif");
            var dispatcher = new McpDispatcher(server, server.executor());

            dispatcher.dispatchNotification("notifications/initialized", null, "sess-notif");
            dispatcher.dispatchNotification("notifications/unknown-thing", null, "sess-notif");

            assertThat(listener.completions).hasSize(2);
            assertThat(listener.completions.get(0).outcome()).isInstanceOf(OperationOutcome.NotificationAccepted.class);
            assertThat(listener.completions.get(1).outcome()).isInstanceOf(OperationOutcome.NotificationIgnored.class);
        }
    }

    @Test
    void statelessNotificationStillReportsIgnoredBeforeAnyEarlyReturn() {
        var listener = new RecordingListener();
        try (ServerEngine server = newEngine(b -> b.session(s -> s.enabled(false))
                .observability(o -> o.listener(listener)))) {
            var dispatcher = new McpDispatcher(server, server.executor());

            dispatcher.dispatchNotification("notifications/initialized", null, null);

            assertThat(listener.starts).hasSize(1);
            assertThat(listener.completions).hasSize(1);
            assertThat(listener.completions.getFirst().outcome()).isInstanceOf(OperationOutcome.NotificationIgnored.class);
        }
    }

    @Test
    @Execution(ExecutionMode.SAME_THREAD)
    void throwingListenerDoesNotAffectHandlerOrOtherListeners() {
        var faulty = new RecordingListener();
        faulty.throwOnStart = new RuntimeException("boom on start");
        faulty.throwOnComplete = new RuntimeException("boom on complete");
        var healthy = new RecordingListener();

        try (ServerEngine server = newEngine(
                b -> b.observability(o -> o.listener(faulty).listener(healthy)))) {
            server.createSession("sess-fault").activate();
            var dispatcher = new McpDispatcher(server, server.executor());

            var result = asResponse(dispatcher
                    .dispatchRequestAsync(RequestId.of(1), "ping", null, "sess-fault")
                    .join());

            assertThat(result.responseBodyString()).contains("result");
            assertThat(healthy.starts).hasSize(1);
            assertThat(healthy.completions).hasSize(1);
            assertThat(healthy.completions.getFirst().outcome()).isInstanceOf(OperationOutcome.Completed.class);
        }
    }

    @Test
    void responseIsIdenticalWithAndWithoutObservationRegistered() {
        try (ServerEngine plain = newEngine(b -> {});
                ServerEngine observed = newEngine(b -> b.observability(o -> o.listener(new RecordingListener())))) {
            plain.createSession("sess-parity").activate();
            observed.createSession("sess-parity").activate();
            var plainDispatcher = new McpDispatcher(plain, plain.executor());
            var observedDispatcher = new McpDispatcher(observed, observed.executor());

            var plainResult =
                    asResponse(plainDispatcher.dispatchRequestAsync(RequestId.of(1), "ping", null, "sess-parity")
                            .join());
            var observedResult =
                    asResponse(observedDispatcher.dispatchRequestAsync(RequestId.of(1), "ping", null, "sess-parity")
                            .join());

            assertThat(observedResult.responseBodyString()).isEqualTo(plainResult.responseBodyString());
            assertThat(observedResult.httpStatus()).isEqualTo(plainResult.httpStatus());
        }
    }
}
