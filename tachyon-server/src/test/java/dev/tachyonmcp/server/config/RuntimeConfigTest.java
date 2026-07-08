/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link RuntimeConfig} defaults and builder null rejection.
 *
 * @author Konstantin Pavlov
 */
class RuntimeConfigTest {

    @Test
    void usesDefaults() {
        var config = RuntimeConfig.builder().build();

        assertThat(config.shutdownGracePeriod()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void buildsWithCustomValues() {
        var config = RuntimeConfig.builder()
                .shutdownGracePeriod(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(30))
                .build();

        assertThat(config.shutdownGracePeriod()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void rejectsNullShutdownGracePeriod() {
        assertThatNullPointerException()
                .isThrownBy(() -> RuntimeConfig.builder().shutdownGracePeriod(null));
    }

    @Test
    void rejectsNullRequestTimeout() {
        assertThatNullPointerException()
                .isThrownBy(() -> RuntimeConfig.builder().requestTimeout(null));
    }
}
