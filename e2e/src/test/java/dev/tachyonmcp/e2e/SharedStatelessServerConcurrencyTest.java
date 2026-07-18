/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SharedStatelessServerConcurrencyTest extends AbstractStatelessMcpE2eTest {

    private static final int CLIENT_COUNT = 32;
    private static final long TIMEOUT_SECONDS = 10;

    @Test
    void parallelInitializationReturnsNoSessionId() throws Exception {
        var latch = new CountDownLatch(CLIENT_COUNT);
        var nullSessionIds = new AtomicInteger();
        var errors = new ConcurrentLinkedQueue<String>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CLIENT_COUNT; i++) {
                executor.submit(() -> {
                    try (var client = createTestClient()) {
                        var sid = client.initialize();
                        if (sid == null) nullSessionIds.incrementAndGet();
                    } catch (Exception e) {
                        errors.add(e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(errors).isEmpty();
        assertThat(nullSessionIds.get()).isEqualTo(CLIENT_COUNT);
    }

    @Test
    void parallelRequestsHaveIsolatedResponses() throws Exception {
        var latch = new CountDownLatch(CLIENT_COUNT);
        var errors = new ConcurrentLinkedQueue<String>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CLIENT_COUNT; i++) {
                var clientId = i;
                executor.submit(() -> {
                    try (var client = createTestClient()) {
                        client.initialize();
                        var response = client.sendRpc("""
                            {"jsonrpc":"2.0","id":%d,"method":"tools/list"}
                            """.formatted(clientId));
                        if (!response.contains("\"tools\"")) {
                            errors.add("client %d: missing tools: %s".formatted(clientId, response));
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
}
