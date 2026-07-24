/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.kotlin.server.features.prompts

import dev.tachyonmcp.kotlin.server.config.PromptScope
import dev.tachyonmcp.kotlin.server.features.runSuspendHandler
import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.features.prompts.PromptHandler
import dev.tachyonmcp.server.features.prompts.PromptRequest
import dev.tachyonmcp.server.features.prompts.PromptResult
import kotlinx.coroutines.CoroutineName

/**
 * Wraps a suspend prompt lambda returning [List]<[dev.tachyonmcp.server.domain.PromptMessage]> into an [dev.tachyonmcp.server.features.prompts.PromptHandler].
 */
@JvmSynthetic
internal fun promptHandler(
    descriptor: dev.tachyonmcp.server.features.prompts.PromptDescriptor,
    block: suspend PromptScope.() -> List<dev.tachyonmcp.server.domain.PromptMessage>,
): PromptHandler {
    val coroutineName = CoroutineName("prompt:${descriptor.name()}")
    return PromptHandler {
        ctx: InteractionContext,
        request: PromptRequest,
        ->
        runSuspendHandler(coroutineName) {
            PromptResult.messages(
                PromptScope(
                    ctx,
                    request,
                ).block(),
            )
        }
    }
}
