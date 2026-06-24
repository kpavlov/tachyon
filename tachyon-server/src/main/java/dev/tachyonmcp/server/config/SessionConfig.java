/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.server.session.SessionLogRouter;
import dev.tachyonmcp.server.session.SessionStore;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

public record SessionConfig(
        boolean stateless,
        Duration sessionTtl,
        @Nullable SessionLogRouter sessionLogRouter,
        @Nullable SessionStore sessionStore) {

    public static final SessionConfig DEFAULT = new SessionConfig(false, Duration.ofSeconds(30), null, null);

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean stateless;
        private Duration sessionTtl = Duration.ofSeconds(30);
        private @Nullable SessionLogRouter sessionLogRouter;
        private @Nullable SessionStore sessionStore;

        private Builder() {}

        public Builder stateless(boolean stateless) {
            this.stateless = stateless;
            return this;
        }

        public Builder sessionTtl(Duration sessionTtl) {
            this.sessionTtl = sessionTtl;
            return this;
        }

        public Builder sessionLogRouter(@Nullable SessionLogRouter router) {
            this.sessionLogRouter = router;
            return this;
        }

        public Builder sessionStore(@Nullable SessionStore store) {
            this.sessionStore = store;
            return this;
        }

        public SessionConfig build() {
            return new SessionConfig(stateless, sessionTtl, sessionLogRouter, sessionStore);
        }
    }
}
