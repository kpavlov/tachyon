/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SharedStatefulServerConcurrencyTest extends AbstractStatefulMcpE2eTest {

    private static final int CLIENT_COUNT = 32;
    private static final int CONCURRENT_REQUEST_COUNT = 8;
    private static final long TIMEOUT_SECONDS = 10;

    @Test
    void parallelInitializationProducesUniqueSessionIds() throws Exception {
        var latch = new CountDownLatch(CLIENT_COUNT);
        var sessionIds = new ConcurrentLinkedQueue<String>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CLIENT_COUNT; i++) {
                executor.submit(() -> {
                    try (var client = createTestClient()) {
                        var sid = client.initialize();
                        if (sid != null) sessionIds.add(sid);
                    } catch (Exception e) {
                        throw new AssertionError("init failed", e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(sessionIds).hasSize(CLIENT_COUNT);
        assertThat(sessionIds).doesNotHaveDuplicates();
    }

    @Test
    void parallelRequestsAcrossSessionsDoNotCrossResponses() throws Exception {
        var latch = new CountDownLatch(CLIENT_COUNT);
        var errors = new ConcurrentLinkedQueue<String>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CLIENT_COUNT; i++) {
                var clientId = i;
                executor.submit(() -> {
                    try (var client = createTestClient()) {
                        var sid = client.initialize();
                        var uniqueMessage = "client-" + clientId;
                        var response = client.post(sid, """
                                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"echo","arguments":{"message":"%s"}}}
                                """.formatted(clientId, uniqueMessage));
                        if (!response.body().contains(uniqueMessage)) {
                            errors.add("client %d: expected echo of '%s' but got: %s"
                                    .formatted(clientId, uniqueMessage, response.body()));
                        }
                    } catch (Exception e) {
                        errors.add("client %d: %s".formatted(clientId, e.getMessage()));
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(errors).isEmpty();
    }

    @Test
    void parallelRequestsWithinOneSession() throws Exception {
        int requestCount = 32;
        try (var client = createTestClient()) {
            var sid = client.initialize();
            var latch = new CountDownLatch(requestCount);
            var errors = new ConcurrentLinkedQueue<String>();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < requestCount; i++) {
                    var reqId = i;
                    executor.submit(() -> {
                        try {
                            var response = client.post(sid, """
                                {"jsonrpc":"2.0","id":%d,"method":"ping"}
                                """.formatted(reqId));
                            if (!response.body().contains("\"result\"")) {
                                errors.add("req %d: unexpected response: %s".formatted(reqId, response.body()));
                            }
                        } catch (Exception e) {
                            errors.add("req %d: %s".formatted(reqId, e.getMessage()));
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            }
            assertThat(errors).isEmpty();
        }
    }

    @Test
    void repeatedJsonRpcIdsAcrossSessionsNoCrossover() throws Exception {
        int sessionCount = 8;
        var sessions = new String[sessionCount];
        for (int i = 0; i < sessionCount; i++) {
            try (var c = createTestClient()) {
                sessions[i] = c.initialize();
            }
        }
        var latch = new CountDownLatch(sessionCount);
        var results = new ConcurrentHashMap<Integer, String>();
        var errors = new ConcurrentLinkedQueue<String>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < sessionCount; i++) {
                var idx = i;
                var sid = sessions[i];
                executor.submit(() -> {
                    try (var client = createTestClient()) {
                        // Every session sends the same JSON-RPC id (1) — a genuine cross-session
                        // mixup must be caught by the echoed message, not by the (identical) id.
                        var response = client.post(sid, """
                            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"echo","arguments":{"message":"session-msg-%d"}}}
                            """.formatted(idx));
                        results.put(idx, response.body());
                    } catch (Exception e) {
                        errors.add("session-%d: %s".formatted(idx, e.getMessage()));
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(errors).isEmpty();
        assertThat(results).hasSize(sessionCount);
        results.forEach((idx, body) -> assertThat(body).contains("session-msg-" + idx));
    }

    @Test
    void deletingOneSessionDoesNotAffectConcurrentRequestsToAnotherSession() throws Exception {
        try (var clientA = createTestClient();
                var clientB = createTestClient()) {
            var sidA = clientA.initialize();
            var sidB = clientB.initialize();

            var ready = new CountDownLatch(CONCURRENT_REQUEST_COUNT);
            var start = new CountDownLatch(1);
            var completed = new CountDownLatch(CONCURRENT_REQUEST_COUNT);
            var errors = new ConcurrentLinkedQueue<String>();
            var responses = new ConcurrentHashMap<Integer, HttpResponse<String>>();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < CONCURRENT_REQUEST_COUNT; i++) {
                    var requestId = i;
                    executor.submit(() -> {
                        try {
                            ready.countDown();
                            if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                                errors.add("B request %d: start barrier timed out".formatted(requestId));
                                return;
                            }
                            var response = clientB.post(sidB, """
                                {"jsonrpc":"2.0","id":%d,"method":"ping"}
                                """.formatted(requestId));
                            responses.put(requestId, response);
                        } catch (Exception e) {
                            errors.add("B request %d: %s".formatted(requestId, e.getMessage()));
                        } finally {
                            completed.countDown();
                        }
                    });
                }
                assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                var deleteResponse = clientA.delete(sidA);
                assertThat(deleteResponse.statusCode()).isEqualTo(200);
                assertThat(completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            }
            assertThat(errors).isEmpty();
            assertThat(responses).hasSize(CONCURRENT_REQUEST_COUNT);
            responses.forEach((requestId, response) -> {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThatJson(response.body()).isEqualTo("""
                        {"jsonrpc":"2.0","id":%d,"result":{}}
                        """.formatted(requestId));
            });
        }
    }
}
