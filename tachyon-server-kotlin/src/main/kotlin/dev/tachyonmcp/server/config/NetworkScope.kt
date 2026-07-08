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
        /** Network interface to bind to. */
        public var host: String? = null

        /** Port to listen on. */
        public var port: Int? = null

        /** Base path for MCP endpoints. */
        public var endpointPath: String? = null

        /** Whether to allow requests with a null Origin header. */
        public var allowNullOrigin: Boolean? = null

        /** Whether to allow connections from private networks. */
        public var allowPrivateNetworks: Boolean? = null

        /** Idle timeout for the reader side of the connection. */
        public var readerIdleTimeout: Duration? = null

        /** Idle timeout for the writer side of the connection. */
        public var writerIdleTimeout: Duration? = null

        /** SSE heartbeat interval for silent listening streams. */
        public var heartbeatInterval: Duration? = null

        /** Maximum allowed content length for incoming requests. */
        public var maxContentLength: Int? = null

        /** Netty I/O engine configuration. */
        public var ioEngine: NettyIoEngine? = null

        /** Allowed CORS origins. */
        public val allowedOrigins: MutableList<String> = mutableListOf()

        /** Allowed CORS headers. */
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
            heartbeatInterval?.let { builder.heartbeatInterval(it.toJavaDuration()) }
            maxContentLength?.let(builder::maxContentLength)
            ioEngine?.let(builder::ioEngine)
        }
    }
