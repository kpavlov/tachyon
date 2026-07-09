/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link ServerBuilder} enforces the thread-per-task executor contract required by
 * blocking-first dispatch.
 *
 * @author Konstantin Pavlov
 */
class ServerBuilderTest {

    @Test
    void rejectsBoundedPool() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TachyonServer.builder()
                        .executor(Executors.newFixedThreadPool(1))
                        .build())
                .withMessageContaining("thread per task");
    }

    @Test
    void acceptsVirtualThreadPerTaskExecutor() throws IOException {
        try (var server = TachyonServer.builder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build()) {
            assertThat(server).isNotNull();
        }
    }

    @Test
    void acceptsDefaultExecutor() throws IOException {
        try (var server = TachyonServer.builder().build()) {
            assertThat(server).isNotNull();
        }
    }
}
