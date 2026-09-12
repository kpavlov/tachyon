/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp.v2025_11_25;

import static dev.tachyonmcp.testkit.McpHttpResponseAssert.assertThatResponse;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.domain.RequestId;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequestCancellationTest extends AbstractStatefulMcpE2eTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationOnlyStopsInboundWorkInOwningSessionWithOverlappingOutboundId(boolean async) throws Exception {
        final var started = new CountDownLatch(1);
        final var interrupted = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var handlerFuture = new CompletableFuture<ToolResult>();
        handlerFuture.whenComplete((result, error) -> {
            if (handlerFuture.isCancelled()) interrupted.countDown();
        });
        startServerWith(s -> {
            if (async) {
                s.tools().registerAsync(b -> b.name("blocked"), (context, request) -> {
                    started.countDown();
                    return handlerFuture;
                });
                return;
            }
            s.tools().register(b -> b.name("blocked"), (context, request) -> {
                started.countDown();
                try {
                    release.await();
                    return ToolResult.text("finished");
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    throw e;
                }
            });
        });
        try (final var owner = createTestClient();
                final var other = createTestClient();
                final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var sessionId = owner.initialize();
            final var otherSessionId = other.initialize();
            final var outbound = new CompletableFuture<String>();
            final var id = RequestId.of("overlap");
            engine().registerPendingRequest(id, sessionId, null, outbound);
            // language=json
            final var request = executor.submit(() -> owner.sendRpc("""
                    {"jsonrpc":"2.0","id":"overlap","method":"tools/call","params":{"name":"blocked"}}
                    """));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatResponse(other.ping(otherSessionId, "overlap"))
                        .isSuccess()
                        .hasId("overlap")
                        .hasResult("{}");
                assertThat(other.notify("notifications/cancelled", Map.of("requestId", "overlap"))
                                .statusCode())
                        .isEqualTo(202);
                assertThat(request.isDone()).isFalse();
                assertThat(outbound).isNotDone();
                assertThat(owner.notify("notifications/cancelled", Map.of("requestId", "overlap"))
                                .statusCode())
                        .isEqualTo(202);
                assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatResponse(request.get(5, TimeUnit.SECONDS))
                        .isJsonRpcError()
                        .hasErrorCode(-32603);
                assertThat(outbound).isNotDone();
                assertThat(owner.notify("notifications/cancelled", Map.of("requestId", "overlap"))
                                .statusCode())
                        .isEqualTo(202);
                assertThat(outbound).isNotDone();
                assertThatResponse(owner.ping(sessionId, "overlap"))
                        .isSuccess()
                        .hasId("overlap")
                        .hasResult("{}");
                assertThatResponse(owner.ping(sessionId, "overlap"))
                        .isSuccess()
                        .hasId("overlap")
                        .hasResult("{}");
                assertThat(engine().completePendingRequest(id, sessionId, null, "{}"))
                        .isTrue();
                assertThat(outbound).isCompletedWithValue("{}");
            } finally {
                release.countDown();
                handlerFuture.complete(ToolResult.text("released"));
            }
        }
    }
}
