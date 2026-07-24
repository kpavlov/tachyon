// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.kotlin.server.TachyonDsl
import dev.tachyonmcp.server.session.SessionEventStore
import dev.tachyonmcp.server.session.SessionIdGenerator
import dev.tachyonmcp.server.session.SessionStore
import io.netty.handler.codec.http.HttpRequest
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@TachyonDsl
public class SessionScope
    @PublishedApi
    internal constructor() {
        /** Whether session management is enabled. */
        public var enabled: Boolean = false

        /** Session time-to-live duration. */
        public var sessionTtl: Duration? = null

        /** Janitor sweep interval. */
        public var janitorInterval: Duration? = null

        /** Custom session store implementation. */
        public var sessionStore: SessionStore? = null

        /** Custom session event store. */
        public var sessionEventStore: SessionEventStore? = null

        /** Session ID generator; defaults to [SessionIdGenerator.DEFAULT] (`sess_<uuid>`). Never null. */
        public var sessionIdGenerator: SessionIdGenerator = SessionIdGenerator.DEFAULT

        /**
         * Lambda-friendly overload, e.g. deriving the id from an authenticated principal:
         * `sessionIdGenerator { req -> principalFrom(req)?.sessionKey ?: SessionIdGenerator.DEFAULT.generate(req) }`.
         * Do not key sessions off an unauthenticated client header — that invites session
         * fixation and cross-tenant collisions, and a missing header would crash the request thread.
         */
        public fun sessionIdGenerator(generator: (HttpRequest) -> String) {
            sessionIdGenerator = SessionIdGenerator { generator(it) }
        }

        @PublishedApi
        internal fun applyTo(builder: dev.tachyonmcp.server.config.SessionConfig.Builder) {
            builder.enabled(enabled)
            sessionTtl?.let { builder.sessionTtl(it.toJavaDuration()) }
            janitorInterval?.let { builder.janitorInterval(it.toJavaDuration()) }
            sessionStore?.let(builder::sessionStore)
            sessionEventStore?.let(builder::sessionEventStore)
            if (enabled) builder.sessionIdGenerator(sessionIdGenerator)
        }
    }
