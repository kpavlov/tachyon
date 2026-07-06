// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.features

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.runBlocking

/**
 * Runs a suspend block with [runBlocking], wrapping cancellation-via-interrupt contract.
 * Shared by all suspend handler factories.
 */
@JvmSynthetic
internal inline fun <T> runSuspendHandler(
    coroutineName: CoroutineName,
    crossinline block: suspend () -> T,
): T = runBlocking(coroutineName) { block() }
