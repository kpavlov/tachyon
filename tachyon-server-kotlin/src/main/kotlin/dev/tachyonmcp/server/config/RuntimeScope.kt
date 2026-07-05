// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.TachyonDsl
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@TachyonDsl
public class RuntimeScope
    @PublishedApi
    internal constructor() {
        /** Grace period for graceful shutdown. */
        public var shutdownGracePeriod: Duration? = null

        @PublishedApi internal fun applyTo(builder: RuntimeConfig.Builder) {
            shutdownGracePeriod?.let { builder.shutdownGracePeriod(it.toJavaDuration()) }
        }
    }
