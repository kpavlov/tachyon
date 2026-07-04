/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.config.RuntimeConfig;
import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Verifies that {@link Server#close()} drains in-flight handlers for the configured
 * {@code shutdownGracePeriod} before force-interrupting them.
 *
 * @author Konstantin Pavlov
 */
@Execution(ExecutionMode.SAME_THREAD)
class ServerShutdownGraceTest {

    @Test
    void defaultGracePeriodIsFiveSeconds() {
        assertThat(RuntimeConfig.DEFAULT.shutdownGracePeriod()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void closeIsImmediateWhenIdle() {
        var server = TachyonServer.builder().build();

        long start = System.nanoTime();
        server.close();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertThat(elapsedMs)
                .as("idle close must not wait for the grace period")
                .isLessThan(1_000L);
    }

    @Test
    void closeWaitsConfiguredGraceThenInterruptsInFlightHandler() throws Exception {
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);

        var server = TachyonServer.builder()
                .runtime(r -> r.shutdownGracePeriod(Duration.ofMillis(400)))
                .tool(new AbstractSyncToolHandler("slow_probe") {
                    @Override
                    public ToolResult handle(InteractionContext context, ToolArgs args) {
                        started.countDown();
                        try {
                            new CountDownLatch(1).await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            interrupted.countDown();
                            Thread.currentThread().interrupt();
                        }
                        return ToolResult.empty();
                    }
                })
                .build();

        server.createSession("sess-slow").activate();
        var dispatcher = new McpDispatcher(server, server.executor());
        dispatcher.dispatchRequestAsync(
                1, "tools/call", Map.of("name", "slow_probe", "arguments", Map.of()), "sess-slow");

        assertThat(started.await(5, TimeUnit.SECONDS))
                .as("handler must be running before close")
                .isTrue();

        long start = System.nanoTime();
        server.close();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertThat(elapsedMs)
                .as("close must wait the configured grace, not the old 10s")
                .isBetween(300L, 3_000L);
        assertThat(interrupted.await(5, TimeUnit.SECONDS))
                .as("in-flight handler must be force-interrupted after the grace period")
                .isTrue();
    }
}
