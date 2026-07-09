@file:Suppress("FunctionName")
@file:JvmName("TachyonServerFactory")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.config.NetworkConfig
import dev.tachyonmcp.server.config.TachyonServerBuilder
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Suppress("FunctionName")
@OptIn(ExperimentalContracts::class)
public inline fun TachyonServer(
    port: Int = NetworkConfig.DEFAULT.port(),
    configure: (@TachyonDsl TachyonServerBuilder).() -> Unit = {},
): TachyonServer {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    val builder = TachyonServerBuilder().apply(configure)
    builder.applyPort(port)
    return builder.start()
}

@Suppress("FunctionName")
@OptIn(ExperimentalContracts::class)
public inline fun buildServer(
    configure: (@TachyonDsl TachyonServerBuilder).() -> Unit = {},
): TachyonServer {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    val builder = TachyonServerBuilder().apply(configure)
    return builder.build()
}
