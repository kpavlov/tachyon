/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class AbstractJanitorTest {

    @Test
    @Timeout(5)
    void sweepRunsPeriodically() throws Exception {
        var count = new AtomicInteger();
        var janitor = new AbstractJanitor("test-janitor") {
            @Override
            protected void sweep() {
                count.incrementAndGet();
            }
        };

        janitor.start(Duration.ofMillis(5));
        try {
            var deadline = System.currentTimeMillis() + 2000;
            while (count.get() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
            assertThat(count.get()).isGreaterThanOrEqualTo(2);
        } finally {
            janitor.close();
        }
    }

    @Test
    @Timeout(5)
    void sweepExceptionDoesNotCancelFutureRuns() throws Exception {
        var count = new AtomicInteger();
        var janitor = new AbstractJanitor("test-janitor") {
            @Override
            protected void sweep() {
                if (count.getAndIncrement() == 0) {
                    throw new RuntimeException("boom");
                }
            }
        };

        janitor.start(Duration.ofMillis(5));
        try {
            var deadline = System.currentTimeMillis() + 2000;
            while (count.get() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
            assertThat(count.get()).isGreaterThanOrEqualTo(2);
        } finally {
            janitor.close();
        }
    }

    @Test
    void startIsIdempotent() throws Exception {
        var count = new AtomicInteger();
        var janitor = new AbstractJanitor("test-janitor") {
            @Override
            protected void sweep() {
                count.incrementAndGet();
            }
        };

        janitor.start(Duration.ofMillis(500));
        janitor.start(Duration.ofMillis(500));
        try {
            Thread.sleep(50);
            assertThat(count.get()).isZero();
        } finally {
            janitor.close();
        }
    }

    @Test
    @Timeout(5)
    void closeStopsSweeping() throws Exception {
        var count = new AtomicInteger();
        var janitor = new AbstractJanitor("test-janitor") {
            @Override
            protected void sweep() {
                count.incrementAndGet();
            }
        };

        janitor.start(Duration.ofMillis(5));
        var deadline = System.currentTimeMillis() + 2000;
        while (count.get() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        janitor.close();

        var countAfterClose = count.get();
        Thread.sleep(50);
        assertThat(count.get()).isEqualTo(countAfterClose);
    }
}
