/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import java.time.Duration;
import java.util.Objects;

public record MonitoringConfig(boolean slowRequestLogging, Duration slowRequestThreshold) {

    public static final Duration DEFAULT_SLOW_REQUEST_THRESHOLD = Duration.ofSeconds(10);
    public static final MonitoringConfig DEFAULT = new MonitoringConfig(false, DEFAULT_SLOW_REQUEST_THRESHOLD);

    public static Builder builder() {
        return new Builder();
    }

    public MonitoringConfig {
        Objects.requireNonNull(slowRequestThreshold, "slowRequestThreshold cannot be null");
    }

    public static final class Builder {
        private boolean slowRequestLogging;
        private Duration slowRequestThreshold = DEFAULT_SLOW_REQUEST_THRESHOLD;

        private Builder() {}

        public Builder slowRequestLogging(boolean slowRequestLogging) {
            this.slowRequestLogging = slowRequestLogging;
            return this;
        }

        public Builder slowRequestLogging() {
            return slowRequestLogging(true);
        }

        public Builder slowRequestThreshold(Duration slowRequestThreshold) {
            this.slowRequestThreshold = Objects.requireNonNull(slowRequestThreshold);
            return this;
        }

        public MonitoringConfig build() {
            return new MonitoringConfig(slowRequestLogging, slowRequestThreshold);
        }
    }
}
