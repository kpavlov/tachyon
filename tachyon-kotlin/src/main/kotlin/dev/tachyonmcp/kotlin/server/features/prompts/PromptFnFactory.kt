// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.prompts

import dev.tachyonmcp.api.runtime.InteractionContext
import dev.tachyonmcp.api.server.domain.PromptMessage
import dev.tachyonmcp.api.server.features.prompts.AsyncPromptFn
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.api.server.features.prompts.PromptRequest
import dev.tachyonmcp.api.server.features.prompts.PromptResult
import dev.tachyonmcp.kotlin.server.config.PromptScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import kotlinx.coroutines.CoroutineName

/**
 * Wraps a suspend prompt lambda returning [List]<[PromptMessage]> into an [AsyncPromptFn].
 */
@JvmSynthetic
internal fun promptFn(
    descriptor: PromptDescriptor,
    runtime: CoroutineRuntime,
    block: suspend PromptScope.() -> List<PromptMessage>,
): AsyncPromptFn {
    val coroutineName = CoroutineName("prompt:${descriptor.name()}")
    return AsyncPromptFn {
        ctx: InteractionContext,
        request: PromptRequest,
        ->
        runtime.future(coroutineName) {
            PromptResult.messages(
                PromptScope(
                    ctx,
                    request,
                ).block(),
            )
        }
    }
}
