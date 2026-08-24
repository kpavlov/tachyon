// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.api.runtime.InteractionContext
import dev.tachyonmcp.api.server.session.SessionIdGenerator
import dev.tachyonmcp.core.server.config.SessionConfig
import dev.tachyonmcp.core.server.session.SessionEventStore
import dev.tachyonmcp.kotlin.server.TachyonDsl
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

        /** Custom session event store. */
        public var sessionEventStore: SessionEventStore? = null

        /** Called with the session id whenever a session is removed (explicit termination or TTL expiry). */
        public var onSessionClosed: ((String) -> Unit)? = null

        /** Session ID generator;
         * defaults to [dev.tachyonmcp.api.server.session.SessionIdGenerator.DEFAULT]
         * (`sess_<uuid>`). Never null.
         */
        public var sessionIdGenerator: SessionIdGenerator<in HttpRequest> =
            SessionIdGenerator.DEFAULT

        /**
         * Lambda-friendly overload, e.g. deriving the id from an authenticated principal:
         * ```kotlin
         * sessionIdGenerator { ctx, req ->
         *     principalFrom(req)?.sessionKey ?: SessionIdGenerator.DEFAULT.generate(ctx, req)
         * }
         * ```
         * Do not key sessions off an unauthenticated client header — that invites session
         * fixation and cross-tenant collisions, and a missing header would crash the request thread.
         */
        public fun sessionIdGenerator(generator: (InteractionContext?, HttpRequest?) -> String) {
            // readsRequest() defaults to true (not overridden below), so the request is never null.
            sessionIdGenerator = SessionIdGenerator { ctx, request -> generator(ctx, request) }
        }

        @PublishedApi
        internal fun applyTo(builder: SessionConfig.Builder) {
            builder.enabled(enabled)
            sessionTtl?.let { builder.sessionTtl(it.toJavaDuration()) }
            janitorInterval?.let { builder.janitorInterval(it.toJavaDuration()) }
            sessionEventStore?.let(builder::sessionEventStore)
            onSessionClosed?.let { listener ->
                builder.onSessionClosed { sessionId -> listener(sessionId) }
            }
            if (enabled) builder.sessionIdGenerator(sessionIdGenerator)
        }
    }
