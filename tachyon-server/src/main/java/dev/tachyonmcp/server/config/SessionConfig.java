/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.server.session.*;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Session lifecycle and persistence configuration.
 *
 * @param enabled            when {@code false} (the default) the server is stateless: no session is
 *                           created and no TTL tracking occurs; set {@code true} to enable sessions
 * @param sessionTtl         duration after which idle sessions are evicted (default 30s)
 * @param sessionLogRouter   optional custom event log router; {@code null} uses in-memory default
 * @param sessionStore       optional custom session store; {@code null} uses in-memory default
 * @param sessionIdGenerator session id generator; defaults to {@link SessionIdGenerator#DEFAULT}
 * @param janitorInterval    interval between janitor sweeps (default 5s);
 *                           {@code null} uses the default
 */
public record SessionConfig(
        boolean enabled,
        @Nullable Duration sessionTtl,
        @Nullable SessionLogRouter sessionLogRouter,
        @Nullable SessionStore sessionStore,
        @Nullable SessionIdGenerator sessionIdGenerator,
        @Nullable Duration janitorInterval) {

    public static final Duration DEFAULT_SESSION_TTL = Duration.ofSeconds(30);
    public static final Duration DEFAULT_JANITOR_INTERVAL = Duration.ofSeconds(5);

    public static final SessionConfig STATELESS = new SessionConfig(false, null, null, null, null, null);

    public SessionConfig {
        if (!enabled) {
            if (sessionIdGenerator != null
                    || sessionStore != null
                    || sessionLogRouter != null
                    || sessionTtl != null
                    || janitorInterval != null) {
                throw new IllegalStateException("Session options require sessions to be enabled — call enabled(true)");
            }
        } else {
            if (sessionTtl == null) sessionTtl = DEFAULT_SESSION_TTL;
            if (janitorInterval == null) janitorInterval = DEFAULT_JANITOR_INTERVAL;
            if (sessionIdGenerator == null) sessionIdGenerator = SessionIdGenerator.DEFAULT;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link SessionConfig}.
     */
    public static final class Builder {
        private boolean enabled = false;
        private @Nullable Duration sessionTtl;
        private @Nullable Duration janitorInterval;
        private @Nullable SessionLogRouter sessionLogRouter;
        private @Nullable SessionStore sessionStore;
        private @Nullable SessionIdGenerator sessionIdGenerator;

        private Builder() {}

        /**
         * Enables server-side sessions. Off by default (the server is stateless), so callers opt in
         * explicitly with {@code enabled(true)}.
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the session TTL (idle sessions are evicted after this duration).
         */
        public Builder sessionTtl(Duration sessionTtl) {
            this.sessionTtl = sessionTtl;
            return this;
        }

        /**
         * Sets the janitor sweep interval.
         */
        public Builder janitorInterval(Duration janitorInterval) {
            this.janitorInterval = janitorInterval;
            return this;
        }

        /**
         * Sets a custom session event log router.
         */
        public Builder sessionLogRouter(@Nullable SessionLogRouter router) {
            this.sessionLogRouter = router;
            return this;
        }

        /**
         * Sets a custom session store implementation.
         */
        public Builder sessionStore(@Nullable SessionStore store) {
            this.sessionStore = store;
            return this;
        }

        /**
         * Sets a custom session id generator (derives the id from the initialize request).
         * {@code null} restores {@link SessionIdGenerator#DEFAULT}.
         */
        public Builder sessionIdGenerator(@Nullable SessionIdGenerator generator) {
            this.sessionIdGenerator = generator;
            return this;
        }

        /**
         * Builds the {@link SessionConfig}.
         */
        public SessionConfig build() {
            if (!enabled) {
                if (sessionIdGenerator != null
                        || sessionStore != null
                        || sessionLogRouter != null
                        || sessionTtl != null
                        || janitorInterval != null) {
                    throw new IllegalStateException(
                            "Session options require sessions to be enabled — call enabled(true)");
                }

                return SessionConfig.STATELESS;
            } else {
                return new SessionConfig(
                        enabled,
                        sessionTtl,
                        sessionLogRouter != null ? sessionLogRouter : new InMemorySessionLogRouter(),
                        sessionStore != null ? sessionStore : new InMemorySessionStore(),
                        sessionIdGenerator,
                        janitorInterval);
            }
        }
    }
}
