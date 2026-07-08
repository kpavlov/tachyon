// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.session.SessionLogRouter
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

        /** Custom session log router. */
        public var sessionLogRouter: SessionLogRouter? = null

        /** Custom session ID generator function. */
        public var sessionIdGenerator: ((HttpRequest) -> String)? = null

        @Deprecated(message = "Use `enabled`")
        public var stateless: Boolean
            get() = !enabled
            set(value) {
                enabled = !value
            }

        /** Lambda-friendly overload: `sessionIdGenerator { it.headers()["X-Tenant-Id"]!! }`. */
        public fun sessionIdGenerator(generator: (HttpRequest) -> String) {
            sessionIdGenerator = generator
        }

        @PublishedApi
        internal fun applyTo(builder: SessionConfig.Builder) {
            builder.enabled(enabled)
            sessionTtl?.let { builder.sessionTtl(it.toJavaDuration()) }
            janitorInterval?.let { builder.janitorInterval(it.toJavaDuration()) }
            sessionStore?.let(builder::sessionStore)
            sessionLogRouter?.let(builder::sessionLogRouter)
            sessionIdGenerator.let { builder.sessionIdGenerator(it) }
        }
    }
