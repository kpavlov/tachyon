// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.config.SessionConfig
import dev.tachyonmcp.server.session.SessionLogRouter
import dev.tachyonmcp.server.session.SessionStore
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@TachyonDsl
public class SessionScope
    @PublishedApi
    internal constructor() {
        public var stateless: Boolean = false
        public var sessionTtl: Duration? = null
        public var shutdownGracePeriod: Duration? = null
        public var sessionStore: SessionStore? = null
        public var sessionLogRouter: SessionLogRouter? = null

        @PublishedApi internal fun applyTo(builder: SessionConfig.Builder) {
            builder.stateless(stateless)
            sessionTtl?.let { builder.sessionTtl(it.toJavaDuration()) }
            shutdownGracePeriod?.let { builder.shutdownGracePeriod(it.toJavaDuration()) }
            sessionStore?.let(builder::sessionStore)
            sessionLogRouter?.let(builder::sessionLogRouter)
        }
    }
