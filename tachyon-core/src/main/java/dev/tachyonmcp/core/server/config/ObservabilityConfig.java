/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.config;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.server.config.MonitoringConfig;
import dev.tachyonmcp.core.server.observability.ObservationListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Configures server observability: slow-request diagnostics, registered {@link
 * ObservationListener}s, and the opt-in {@link PayloadCapturePolicy}. An empty listener list
 * disables the passive observation lifecycle.
 *
 * @param slowRequestLogging whether slow-request diagnostics are enabled
 * @param slowRequestThreshold duration after which a request is considered slow
 * @param listeners      registered listeners, in registration order (append-only)
 * @param payloadCapture opt-in content-capture policy
 */
@ExperimentalApi
public record ObservabilityConfig(
        boolean slowRequestLogging,
        Duration slowRequestThreshold,
        List<ObservationListener> listeners,
        PayloadCapturePolicy payloadCapture)
        implements MonitoringConfig {

    private static final ObservabilityConfig DISABLED = new ObservabilityConfig(
            MonitoringConfig.DEFAULT.slowRequestLogging(),
            MonitoringConfig.DEFAULT.slowRequestThreshold(),
            List.of(),
            PayloadCapturePolicy.DISABLED);

    public ObservabilityConfig {
        Objects.requireNonNull(slowRequestThreshold, "slowRequestThreshold cannot be null");
        listeners = List.copyOf(listeners);
        Objects.requireNonNull(payloadCapture, "payloadCapture cannot be null");
    }

    public static ObservabilityConfig disabled() {
        return DISABLED;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link ObservabilityConfig}. */
    public static final class Builder {

        private boolean slowRequestLogging = MonitoringConfig.DEFAULT.slowRequestLogging();
        private Duration slowRequestThreshold = MonitoringConfig.DEFAULT.slowRequestThreshold();
        private final List<ObservationListener> listeners = new ArrayList<>();
        private PayloadCapturePolicy payloadCapture = PayloadCapturePolicy.DISABLED;

        private Builder() {}

        /**
         * Sets whether slow-request diagnostics are enabled.
         */
        public Builder slowRequestLogging(boolean enabled) {
            this.slowRequestLogging = enabled;
            return this;
        }

        /**
         * Enables slow-request diagnostics.
         */
        public Builder slowRequestLogging() {
            return slowRequestLogging(true);
        }

        /**
         * Sets the duration after which a request is considered slow.
         */
        public Builder slowRequestThreshold(Duration threshold) {
            this.slowRequestThreshold = Objects.requireNonNull(threshold, "slowRequestThreshold cannot be null");
            return this;
        }

        /**
         * Registers a listener. Only application code that bridges into a bundled integration (e.g.
         * {@code tachyon-opentelemetry}'s telemetry listener) normally implements {@link ObservationListener}
         * directly.
         */
        public Builder listener(ObservationListener listener) {
            listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
            return this;
        }

        /** Configures the opt-in content-capture policy. */
        public Builder payloadCapture(Consumer<PayloadCapturePolicy.Builder> configurer) {
            var builder = PayloadCapturePolicy.builder();
            configurer.accept(builder);
            this.payloadCapture = builder.build();
            return this;
        }

        /**
         * Builds the observability configuration.
         */
        public ObservabilityConfig build() {
            return new ObservabilityConfig(
                    slowRequestLogging, slowRequestThreshold, List.copyOf(listeners), payloadCapture);
        }
    }
}
