/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MonitoringConfigTest {

    @Test
    void usesDefaults() {
        var config = MonitoringConfig.builder().build();

        assertThat(config.slowRequestLogging()).isFalse();
        assertThat(config.slowRequestThreshold()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void builderSlowRequestLoggingConvenience() {
        var config = MonitoringConfig.builder().slowRequestLogging().build();

        assertThat(config.slowRequestLogging()).isTrue();
    }

    @Test
    void builderOverridesThreshold() {
        var config = MonitoringConfig.builder()
                .slowRequestThreshold(Duration.ofSeconds(30))
                .build();

        assertThat(config.slowRequestThreshold()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void rejectsNullThreshold() {
        assertThatNullPointerException()
                .isThrownBy(() -> MonitoringConfig.builder().slowRequestThreshold(null));
    }
}
