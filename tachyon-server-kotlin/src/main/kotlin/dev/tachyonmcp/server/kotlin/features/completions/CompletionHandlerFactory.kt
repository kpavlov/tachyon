/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.kotlin.features.completions

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.features.completions.CompletionHandler
import dev.tachyonmcp.server.features.completions.CompletionRequest
import dev.tachyonmcp.server.features.completions.CompletionResult
import dev.tachyonmcp.server.kotlin.config.CompletionScope
import dev.tachyonmcp.server.kotlin.features.runSuspendHandler
import kotlinx.coroutines.CoroutineName

/**
 * Wraps a suspend completion lambda into a [dev.tachyonmcp.server.features.completions.CompletionHandler].
 * Cancellation is delivered via [Thread.interrupt] of the executing virtual thread,
 * which propagates through [kotlinx.coroutines.runBlocking] to cancel the coroutine.
 */
@JvmSynthetic
internal fun promptCompletionHandler(
    promptName: String,
    block:
        suspend CompletionScope.() -> CompletionResult,
): CompletionHandler {
    val coroutineName = CoroutineName("completion:$promptName")
    return CompletionHandler {
        ctx: InteractionContext,
        request: CompletionRequest,
        ->
        runSuspendHandler(coroutineName) {
            CompletionScope(ctx, request).block()
        }
    }
}

@JvmSynthetic
internal fun resourceCompletionHandler(
    uriOrTemplate: String,
    block:
        suspend CompletionScope.() -> CompletionResult,
): CompletionHandler {
    val coroutineName = CoroutineName("completion:$uriOrTemplate")
    return CompletionHandler {
        ctx: InteractionContext,
        request: CompletionRequest,
        ->
        runSuspendHandler(coroutineName) {
            CompletionScope(ctx, request).block()
        }
    }
}
