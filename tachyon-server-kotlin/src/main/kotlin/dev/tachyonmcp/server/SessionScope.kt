// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.config.SessionConfig
import dev.tachyonmcp.server.session.SessionLogRouter
import dev.tachyonmcp.server.session.SessionStore
import io.netty.handler.codec.http.HttpRequest
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@TachyonDsl
public class SessionScope
    @PublishedApi
    internal constructor() {
        public var enabled: Boolean = false
        public var sessionTtl: Duration? = null
        public var sessionStore: SessionStore? = null
        public var sessionLogRouter: SessionLogRouter? = null
        public var sessionIdGenerator: ((HttpRequest) -> String)? = null

        @Deprecated(message = "Use `enabled`", replaceWith = ReplaceWith("!enabled"))
        public var stateless: Boolean
            get() = !enabled
            set(value) {
                enabled = !value
            }

        /** Lambda-friendly overload: `sessionIdGenerator { it.headers()["X-Tenant-Id"]!! }`. */
        public fun sessionIdGenerator(generator: (HttpRequest) -> String) {
            sessionIdGenerator = { request -> generator(request) }
        }

        @PublishedApi
        internal fun applyTo(builder: SessionConfig.Builder) {
            builder.enabled(enabled)
            sessionTtl?.let { builder.sessionTtl(it.toJavaDuration()) }
            sessionStore?.let(builder::sessionStore)
            sessionLogRouter?.let(builder::sessionLogRouter)
            sessionIdGenerator.let { builder.sessionIdGenerator(it) }
        }
    }
