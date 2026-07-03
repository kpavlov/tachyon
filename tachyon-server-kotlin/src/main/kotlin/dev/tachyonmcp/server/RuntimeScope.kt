// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.config.RuntimeConfig
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@TachyonDsl
public class RuntimeScope
    @PublishedApi
    internal constructor() {
        public var shutdownGracePeriod: Duration? = null

        @PublishedApi internal fun applyTo(builder: RuntimeConfig.Builder) {
            shutdownGracePeriod?.let { builder.shutdownGracePeriod(it.toJavaDuration()) }
        }
    }
