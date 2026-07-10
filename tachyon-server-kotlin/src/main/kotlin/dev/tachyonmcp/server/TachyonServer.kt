@file:Suppress("FunctionName")
@file:JvmName("TachyonServerFactory")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.config.TachyonServerBuilder
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Creates a [TachyonServer] and starts the Netty transport on [port].
 * The returned server is bound and listening — close it with [TachyonServer.close].
 *
 * Use `port = 0` for an ephemeral port (discoverable via [TachyonServer.port]).
 *
 * @see buildServer for the non-listening variant
 */
@Suppress("FunctionName")
@OptIn(ExperimentalContracts::class)
public inline fun TachyonServer(
    port: Int? = null,
    configure: (@TachyonDsl TachyonServerBuilder).() -> Unit = {},
): TachyonServer {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    val builder = TachyonServerBuilder().apply(configure)
    builder.applyPort(port)
    return builder.start()
}

/**
 * Creates and starts a [TachyonServer] on [port]. Alias for [TachyonServer].
 *
 * @see TachyonServer
 */
@OptIn(ExperimentalContracts::class)
public inline fun tachyonServer(
    port: Int? = null,
    configure: (@TachyonDsl TachyonServerBuilder).() -> Unit = {},
): TachyonServer {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    val builder = TachyonServerBuilder().apply(configure)
    builder.applyPort(port)
    return builder.start()
}

/**
 * Builds a fully configured [TachyonServer] **without** starting Netty transport.
 * The returned server is not listening — it supports dynamic registration
 * ([TachyonServer.registerTool] etc.) but has no bound port.
 *
 * Use this for tests that don't need transport, or to set up handlers before
 * binding. To start, create with [TachyonServer] instead.
 */
@Suppress("FunctionName")
@OptIn(ExperimentalContracts::class)
public inline fun buildServer(
    configure: (@TachyonDsl TachyonServerBuilder).() -> Unit = {},
): TachyonServer {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    val builder = TachyonServerBuilder().apply(configure)
    return builder.build()
}
