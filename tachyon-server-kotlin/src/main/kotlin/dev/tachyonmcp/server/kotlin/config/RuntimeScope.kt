// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.kotlin.config

import dev.tachyonmcp.server.config.RuntimeConfig
import dev.tachyonmcp.server.kotlin.TachyonDsl
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@TachyonDsl
public class RuntimeScope
    @PublishedApi
    internal constructor() {
        /** Grace period for graceful shutdown. */
        public var shutdownGracePeriod: Duration? = null

        /** Timeout for pending requests sent to the client (default 60s). */
        public var requestTimeout: Duration? = null

        @PublishedApi
        internal fun applyTo(builder: RuntimeConfig.Builder) {
            shutdownGracePeriod?.let { builder.shutdownGracePeriod(it.toJavaDuration()) }
            requestTimeout?.let { builder.requestTimeout(it.toJavaDuration()) }
        }
    }
