// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.completions

import dev.tachyonmcp.kotlin.server.config.CompletionScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.protocol.api.runtime.InteractionContext
import dev.tachyonmcp.protocol.api.server.features.completions.AsyncCompletionHandler
import dev.tachyonmcp.protocol.api.server.features.completions.CompletionRequest
import dev.tachyonmcp.protocol.api.server.features.completions.CompletionResult
import kotlinx.coroutines.CoroutineName

@JvmSynthetic
internal fun promptCompletionHandler(
    promptName: String,
    runtime: CoroutineRuntime,
    block:
        suspend CompletionScope.() -> CompletionResult,
): AsyncCompletionHandler {
    val coroutineName = CoroutineName("completion:$promptName")
    return AsyncCompletionHandler {
        ctx: InteractionContext,
        request: CompletionRequest,
        ->
        runtime.future(coroutineName) {
            CompletionScope(ctx, request).block()
        }
    }
}

@JvmSynthetic
internal fun resourceCompletionHandler(
    uriOrTemplate: String,
    runtime: CoroutineRuntime,
    block:
        suspend CompletionScope.() -> CompletionResult,
): AsyncCompletionHandler {
    val coroutineName = CoroutineName("completion:$uriOrTemplate")
    return AsyncCompletionHandler {
        ctx: InteractionContext,
        request: CompletionRequest,
        ->
        runtime.future(coroutineName) {
            CompletionScope(ctx, request).block()
        }
    }
}
