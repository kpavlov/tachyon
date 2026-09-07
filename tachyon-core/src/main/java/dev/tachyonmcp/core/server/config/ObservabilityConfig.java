/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.config;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.core.server.observability.ObservationListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Configures the passive MCP observation lifecycle: registered {@link ObservationListener}s and
 * the opt-in {@link PayloadCapturePolicy}. An empty listener list is the disabled state — the
 * dispatcher takes its unchanged fast path with no allocation and no executor hop.
 *
 * @param listeners      registered listeners, in registration order (append-only)
 * @param payloadCapture opt-in content-capture policy
 */
@ExperimentalApi
public record ObservabilityConfig(List<ObservationListener> listeners, PayloadCapturePolicy payloadCapture) {

    public static final ObservabilityConfig DISABLED = new ObservabilityConfig(List.of(), PayloadCapturePolicy.DISABLED);

    public ObservabilityConfig {
        listeners = List.copyOf(listeners);
        Objects.requireNonNull(payloadCapture, "payloadCapture cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link ObservabilityConfig}. */
    public static final class Builder {

        private final List<ObservationListener> listeners = new ArrayList<>();
        private PayloadCapturePolicy payloadCapture = PayloadCapturePolicy.DISABLED;

        private Builder() {}

        /**
         * Registers a listener. Only application code that bridges into a bundled integration (e.g.
         * {@code tachyon-otel}'s telemetry listener) normally implements {@link ObservationListener}
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

        public ObservabilityConfig build() {
            return new ObservabilityConfig(List.copyOf(listeners), payloadCapture);
        }
    }
}
