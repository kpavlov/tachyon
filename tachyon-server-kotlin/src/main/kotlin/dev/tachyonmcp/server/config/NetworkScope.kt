// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.config.NetworkConfig
import dev.tachyonmcp.transport.netty.NettyIoEngine
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@TachyonDsl
public class NetworkScope
    @PublishedApi
    internal constructor() {
        public var host: String? = null
        public var port: Int? = null
        public var endpointPath: String? = null
        public var allowNullOrigin: Boolean? = null
        public var allowPrivateNetworks: Boolean? = null
        public var readerIdleTimeout: Duration? = null
        public var writerIdleTimeout: Duration? = null
        public var maxContentLength: Int? = null
        public var ioEngine: NettyIoEngine? = null
        public val allowedOrigins: MutableList<String> = mutableListOf()
        public val allowedHeaders: MutableList<String> = mutableListOf()

        @PublishedApi internal fun applyTo(builder: NetworkConfig.Builder) {
            host?.let(builder::host)
            port?.let(builder::port)
            endpointPath?.let(builder::endpointPath)
            if (allowedOrigins.isNotEmpty()) builder.allowedOrigins(*allowedOrigins.toTypedArray())
            allowNullOrigin?.let(builder::allowNullOrigin)
            allowPrivateNetworks?.let(builder::allowPrivateNetworks)
            if (allowedHeaders.isNotEmpty()) builder.allowedHeaders(*allowedHeaders.toTypedArray())
            readerIdleTimeout?.let { builder.readerIdleTimeout(it.toJavaDuration()) }
            writerIdleTimeout?.let { builder.writerIdleTimeout(it.toJavaDuration()) }
            maxContentLength?.let(builder::maxContentLength)
            ioEngine?.let(builder::ioEngine)
        }
    }
