// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Suppress("FunctionName")
@OptIn(ExperimentalContracts::class)
public inline fun TachyonServer(
    port: Int? = null,
    configure: (@TachyonDsl TachyonServerBuilder).() -> Unit = {},
): McpServerHandle {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    return tachyonServer(port, configure)
}

@OptIn(ExperimentalContracts::class)
public inline fun tachyonServer(
    port: Int? = null,
    configure: (@TachyonDsl TachyonServerBuilder).() -> Unit = {},
): McpServerHandle {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    val builder = TachyonServerBuilder().apply(configure)
    builder.applyPort(port)
    return builder.start()
}

@OptIn(ExperimentalContracts::class)
public inline fun buildServer(
    configure: (@TachyonDsl TachyonServerBuilder).() -> Unit = {},
): McpServer {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    return TachyonServerBuilder().apply(configure).build()
}
