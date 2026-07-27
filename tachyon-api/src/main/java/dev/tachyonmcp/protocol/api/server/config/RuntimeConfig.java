/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.protocol.api.server.config;

import java.time.Duration;
import org.immutables.value.Value;

/**
 * Handler-execution runtime configuration: settings governing how in-flight handlers are drained
 * on shutdown. Distinct from the executor/thread-factory <em>wiring</em>, which is injected on the
 * builder; this type holds only runtime <em>data</em>.
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface RuntimeConfig {

    /**
     * Time an owned executor is given to drain in-flight handlers on {@code close()} before
     * they are force-interrupted (default 5s). {@code Duration.ZERO} interrupts running
     * handlers immediately.
     */
    @Value.Default
    default Duration shutdownGracePeriod() {
        return Duration.ofSeconds(5);
    }

    /**
     * Timeout for pending requests sent to the client (default 60s).
     */
    @Value.Default
    default Duration requestTimeout() {
        return Duration.ofSeconds(60);
    }

    Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);
    RuntimeConfig DEFAULT = DefaultRuntimeConfig.of(Duration.ofSeconds(5), Duration.ofSeconds(60));

    static Builder builder() {
        return DefaultRuntimeConfig.builder();
    }

    /** Builder for {@link RuntimeConfig}. */
    abstract class Builder {

        Builder() {}

        /**
         * Sets the shutdown grace period: how long an owned executor is given to drain in-flight
         * handlers on {@code close()} before they are force-interrupted. {@code Duration.ZERO}
         * interrupts running handlers immediately.
         */
        public abstract Builder shutdownGracePeriod(Duration shutdownGracePeriod);

        /**
         * Sets the timeout for pending requests sent to the client (default 60s).
         */
        public abstract Builder requestTimeout(Duration requestTimeout);

        /** Builds the {@link RuntimeConfig}. */
        public abstract RuntimeConfig build();
    }
}
