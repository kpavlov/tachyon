/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import static dev.tachyonmcp.core.test.TestUtils.newEngine;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.domain.RequestId;
import dev.tachyonmcp.core.protocol.Protocols;
import dev.tachyonmcp.core.runtime.SseEvent;
import dev.tachyonmcp.core.server.McpDispatcher;
import dev.tachyonmcp.core.server.OutboundSseStream;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.server.observability.ObservationListener;
import dev.tachyonmcp.core.server.observability.ObservationScope;
import dev.tachyonmcp.core.server.observability.OperationInfo;
import dev.tachyonmcp.core.server.observability.OperationOutcome;
import dev.tachyonmcp.core.server.session.DefaultDispatchContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@code subscriptions/listen} is the one handler whose returned {@code CompletionStage} spans
 * the whole SSE stream lifetime, resolving only on disconnect or shutdown. Observation must treat
 * stream establishment as the operation's terminal fact and must not wait for the stream to end.
 */
class SubscriptionsListenObservationTest {

    private static final class FakeStream implements OutboundSseStream {
        private final List<SseEvent> events = new CopyOnWriteArrayList<>();
        private volatile @Nullable Runnable onClose;

        @Override
        public void start() {}

        @Override
        public boolean started() {
            return true;
        }

        @Override
        public void writeEvent(@Nullable SseEvent event) {
            if (event != null) events.add(event);
        }

        @Override
        public void close() {}

        @Override
        public void onClose(Runnable callback) {
            this.onClose = callback;
        }

        void disconnect() {
            var callback = onClose;
            if (callback != null) callback.run();
        }
    }

    @Test
    void establishmentCompletesObservationWithoutWaitingForStreamEnd() throws Exception {
        var established = new CompletableFuture<OperationOutcome>();
        var starts = new CopyOnWriteArrayList<OperationInfo>();
        var completions = new CopyOnWriteArrayList<OperationOutcome>();
        ObservationListener listener = new ObservationListener() {
            @Override
            public ObservationScope start(OperationInfo info) {
                starts.add(info);
                return ObservationScope.NOOP;
            }

            @Override
            public void complete(OperationInfo info, OperationOutcome outcome) {
                completions.add(outcome);
                established.complete(outcome);
            }
        };

        try (ServerEngine server = newEngine(b -> b.observability(o -> o.listener(listener)))) {
            var session = server.createSession("sess_sub_listen_obs");
            session.activate();
            var dispatcher = new McpDispatcher(server, server.executor());

            var protocol = Protocols.list().stream()
                    .filter(p -> p.versionString().equals("2026-07-28"))
                    .findFirst()
                    .orElseThrow();
            var ctx = DefaultDispatchContext.create(protocol, server);
            var stream = new FakeStream();

            var future = dispatcher.dispatchRequestAsync(
                    RequestId.of(1), "subscriptions/listen", Map.of(), session.id(), stream, ctx);

            // Establishment is synchronous inside the handler -- complete() must fire promptly even
            // though `future` itself stays pending for the stream's whole lifetime.
            var outcome = established.get(5, TimeUnit.SECONDS);
            assertThat(starts).hasSize(1);
            assertThat(outcome).isInstanceOf(OperationOutcome.StreamEstablished.class);
            assertThat(future)
                    .as("the handler's own future spans the stream lifetime")
                    .isNotDone();

            stream.disconnect();
            future.join();
            // Disconnect resolves the handler's own future but must not re-fire observation.
            assertThat(completions).hasSize(1);
        }
    }
}
