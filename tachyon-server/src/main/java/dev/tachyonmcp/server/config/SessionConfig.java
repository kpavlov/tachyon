/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.server.session.SessionLogRouter;
import dev.tachyonmcp.server.session.SessionStore;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Session lifecycle and persistence configuration.
 *
 * @param stateless           when {@code true}, no session is created and no TTL tracking occurs
 * @param sessionTtl          duration after which idle sessions are evicted (default 30s)
 * @param shutdownGracePeriod time an owned executor is given to drain in-flight handlers on
 *                            {@code close()} before they are force-interrupted (default 5s)
 * @param sessionLogRouter    optional custom event log router; {@code null} uses in-memory default
 * @param sessionStore        optional custom session store; {@code null} uses in-memory default
 */
public record SessionConfig(
        boolean stateless,
        Duration sessionTtl,
        Duration shutdownGracePeriod,
        @Nullable SessionLogRouter sessionLogRouter,
        @Nullable SessionStore sessionStore) {

    public static final SessionConfig DEFAULT =
            new SessionConfig(false, Duration.ofSeconds(30), Duration.ofSeconds(5), null, null);

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link SessionConfig}. */
    public static final class Builder {
        private boolean stateless;
        private Duration sessionTtl = Duration.ofSeconds(30);
        private Duration shutdownGracePeriod = Duration.ofSeconds(5);
        private @Nullable SessionLogRouter sessionLogRouter;
        private @Nullable SessionStore sessionStore;

        private Builder() {}

        /** Enables stateless mode (no session persistence). */
        public Builder stateless(boolean stateless) {
            this.stateless = stateless;
            return this;
        }

        /** Sets the session TTL (idle sessions are evicted after this duration). */
        public Builder sessionTtl(Duration sessionTtl) {
            this.sessionTtl = sessionTtl;
            return this;
        }

        /**
         * Sets the shutdown grace period: how long an owned executor is given to drain in-flight
         * handlers on {@code close()} before they are force-interrupted. {@code Duration.ZERO}
         * interrupts running handlers immediately.
         */
        public Builder shutdownGracePeriod(Duration shutdownGracePeriod) {
            this.shutdownGracePeriod = shutdownGracePeriod;
            return this;
        }

        /** Sets a custom session event log router. */
        public Builder sessionLogRouter(@Nullable SessionLogRouter router) {
            this.sessionLogRouter = router;
            return this;
        }

        /** Sets a custom session store implementation. */
        public Builder sessionStore(@Nullable SessionStore store) {
            this.sessionStore = store;
            return this;
        }

        /** Builds the {@link SessionConfig}. */
        public SessionConfig build() {
            return new SessionConfig(stateless, sessionTtl, shutdownGracePeriod, sessionLogRouter, sessionStore);
        }
    }
}
