// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.prompts

import dev.tachyonmcp.kotlin.server.config.PromptScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.protocol.api.runtime.InteractionContext
import dev.tachyonmcp.protocol.api.server.domain.PromptMessage
import dev.tachyonmcp.protocol.api.server.features.prompts.AsyncPromptHandler
import dev.tachyonmcp.protocol.api.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.protocol.api.server.features.prompts.PromptRequest
import dev.tachyonmcp.protocol.api.server.features.prompts.PromptResult
import kotlinx.coroutines.CoroutineName

/**
 * Wraps a suspend prompt lambda returning [List]<[dev.tachyonmcp.protocol.api.server.domain.PromptMessage]> into an [dev.tachyonmcp.protocol.api.server.features.prompts.PromptHandler].
 */
@JvmSynthetic
internal fun promptHandler(
    descriptor: PromptDescriptor,
    runtime: CoroutineRuntime,
    block: suspend PromptScope.() -> List<PromptMessage>,
): AsyncPromptHandler {
    val coroutineName = CoroutineName("prompt:${descriptor.name()}")
    return AsyncPromptHandler {
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
