/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Handler-execution runtime configuration: settings governing how in-flight handlers are drained
 * on shutdown. Distinct from the executor/thread-factory <em>wiring</em>, which is injected on the
 * builder; this record holds only runtime <em>data</em>.
 *
 * @param shutdownGracePeriod time an owned executor is given to drain in-flight handlers on
 *                            {@code close()} before they are force-interrupted (default 5s);
 *                            {@code Duration.ZERO} interrupts running handlers immediately
 * @param requestTimeout     timeout for pending requests sent to the client (default 60s)
 */
public record RuntimeConfig(Duration shutdownGracePeriod, Duration requestTimeout) {

    static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);
    static final RuntimeConfig DEFAULT = new RuntimeConfig(Duration.ofSeconds(5), DEFAULT_REQUEST_TIMEOUT);

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RuntimeConfig}.
     */
    public static final class Builder {
        private Duration shutdownGracePeriod = DEFAULT.shutdownGracePeriod;
        private Duration requestTimeout = DEFAULT.requestTimeout;

        private Builder() {
            Objects.requireNonNull(shutdownGracePeriod, "shutdownGracePeriod cannot be null");
        }

        /**
         * Sets the shutdown grace period: how long an owned executor is given to drain in-flight
         * handlers on {@code close()} before they are force-interrupted. {@code Duration.ZERO}
         * interrupts running handlers immediately.
         */
        public Builder shutdownGracePeriod(Duration shutdownGracePeriod) {
            this.shutdownGracePeriod = Objects.requireNonNull(shutdownGracePeriod);
            return this;
        }

        /**
         * Sets the timeout for pending requests sent to the client (default 60s).
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout cannot be null");
            return this;
        }

        /**
         * Builds the {@link RuntimeConfig}.
         */
        public RuntimeConfig build() {
            return new RuntimeConfig(shutdownGracePeriod, requestTimeout);
        }
    }
}
