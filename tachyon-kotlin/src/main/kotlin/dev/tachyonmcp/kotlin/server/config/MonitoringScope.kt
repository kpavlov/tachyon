// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.kotlin.server.TachyonDsl
import dev.tachyonmcp.server.config.MonitoringConfig
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@TachyonDsl
public class MonitoringScope
    @PublishedApi
    internal constructor() {
        public var slowRequestLogging: Boolean? = null

        public var slowRequestThreshold: Duration? = null

        @PublishedApi
        internal fun applyTo(builder: MonitoringConfig.Builder) {
            slowRequestLogging?.let { builder.slowRequestLogging(it) }
            slowRequestThreshold?.let { builder.slowRequestThreshold(it.toJavaDuration()) }
        }
    }
