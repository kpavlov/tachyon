/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.server.session.DefaultMcpContext;
import dev.tachyonmcp.server.session.DispatchContext;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class HandlerFuturesTest {

    @Test
    void shouldReturnResultForAlreadyCompleteStage() throws Exception {
        var future = CompletableFuture.completedFuture("done");
        assertThat(HandlerFutures.joinInterruptibly(future)).isEqualTo("done");
    }

    @Test
    void shouldUnwrapRuntimeException() {
        var cause = new IllegalArgumentException("boom");
        var future = CompletableFuture.failedFuture(cause);
        assertThatThrownBy(() -> HandlerFutures.joinInterruptibly(future))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("boom");
    }

    @Test
    void shouldWrapCheckedExceptionInCompletionException() {
        var cause = new Exception("checked");
        var future = CompletableFuture.failedFuture(cause);
        assertThatThrownBy(() -> HandlerFutures.joinInterruptibly(future))
                .isInstanceOf(CompletionException.class)
                .hasCause(cause);
    }

    @Test
    void shouldCompleteOnAlreadyCompleteStage() {
        var future = CompletableFuture.completedFuture("done");
        BiFunction<Object, Throwable, Object> transform = (result, e) -> "transformed:" + result;
        var result = HandlerFutures.completeOn(future, noopContext(), transform);
        assertThat(result).succeedsWithin(Duration.ofSeconds(1)).isEqualTo("transformed:done");
    }

    @Test
    void shouldCompleteOnWithException() {
        var future = CompletableFuture.failedFuture(new RuntimeException("boom"));
        BiFunction<Object, Throwable, Object> transform = (result, e) -> "error:" + e.getMessage();
        var result = HandlerFutures.completeOn(future, noopContext(), transform);
        assertThat(result).succeedsWithin(Duration.ofSeconds(1)).isEqualTo("error:boom");
    }

    @Test
    void shouldCompleteOnUnwrapsCompletionException() {
        var future = CompletableFuture.failedFuture(new CompletionException(new RuntimeException("wrapped")));
        BiFunction<Object, Throwable, Object> transform = (result, e) -> "error:" + e.getMessage();
        var result = HandlerFutures.completeOn(future, noopContext(), transform);
        assertThat(result).succeedsWithin(Duration.ofSeconds(1)).isEqualTo("error:wrapped");
    }

    @Test
    void shouldThrowInterruptedExceptionAndRestoreInterruptFlag() throws Exception {
        var exRef = new AtomicReference<Exception>();
        var started = new CountDownLatch(1);
        var blocked = new CountDownLatch(1);

        var blockingFuture = new CompletableFuture<String>() {
            @Override
            public String get() throws InterruptedException, java.util.concurrent.ExecutionException {
                blocked.countDown();
                return super.get();
            }
        };

        var t = new Thread(() -> {
            started.countDown();
            try {
                HandlerFutures.joinInterruptibly(blockingFuture);
            } catch (Exception e) {
                exRef.set(e);
            }
        });
        t.start();
        started.await(5, TimeUnit.SECONDS);
        blocked.await(5, TimeUnit.SECONDS);
        t.interrupt();
        t.join(5000);

        assertThat(exRef.get()).isInstanceOf(InterruptedException.class);
        assertThat(t.isInterrupted()).isTrue();
    }

    private static DispatchContext noopContext() {
        return DefaultMcpContext.noop();
    }
}
