// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.completions

import dev.tachyonmcp.api.runtime.InteractionContext
import dev.tachyonmcp.api.server.features.completions.AsyncCompletionFn
import dev.tachyonmcp.api.server.features.completions.CompletionRequest
import dev.tachyonmcp.api.server.features.completions.CompletionResult
import dev.tachyonmcp.kotlin.server.config.CompletionScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import kotlinx.coroutines.CoroutineName

@JvmSynthetic
internal fun promptCompletionFn(
    promptName: String,
    runtime: CoroutineRuntime,
    block:
        suspend CompletionScope.() -> CompletionResult,
): AsyncCompletionFn {
    val coroutineName = CoroutineName("completion:$promptName")
    return AsyncCompletionFn {
        ctx: InteractionContext,
        request: CompletionRequest,
        ->
        runtime.future(coroutineName) {
            CompletionScope(ctx, request).block()
        }
    }
}

@JvmSynthetic
internal fun resourceCompletionFn(
    uriOrTemplate: String,
    runtime: CoroutineRuntime,
    block:
        suspend CompletionScope.() -> CompletionResult,
): AsyncCompletionFn {
    val coroutineName = CoroutineName("completion:$uriOrTemplate")
    return AsyncCompletionFn {
        ctx: InteractionContext,
        request: CompletionRequest,
        ->
        runtime.future(coroutineName) {
            CompletionScope(ctx, request).block()
        }
    }
}
