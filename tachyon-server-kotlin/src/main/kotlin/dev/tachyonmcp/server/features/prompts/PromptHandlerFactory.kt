/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.config.PromptScope
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.features.runSuspendHandler
import kotlinx.coroutines.CoroutineName

/**
 * Wraps a suspend prompt lambda returning [List]<[PromptMessage]> into an [InputRequiredPromptHandler].
 */
@JvmSynthetic
internal fun promptHandler(
    descriptor: PromptDescriptor,
    block: suspend PromptScope.() -> List<PromptMessage>,
): InputRequiredPromptHandler {
    val coroutineName = CoroutineName("prompt:${descriptor.name()}")
    return InputRequiredPromptHandler { ctx: InteractionContext, request: PromptRequest ->
        runSuspendHandler(coroutineName) {
            PromptHandlerResult.messages(PromptScope(ctx, request).block())
        }
    }
}
