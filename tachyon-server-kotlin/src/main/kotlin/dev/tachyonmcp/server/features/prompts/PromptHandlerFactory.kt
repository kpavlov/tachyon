/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.prompts

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.config.PromptScope
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.features.runSuspendHandler
import kotlinx.coroutines.CoroutineName

/**
 * Wraps a suspend prompt lambda returning [List]<[PromptMessage]> into an [PromptHandler].
 */
@JvmSynthetic
internal fun promptHandler(
    descriptor: PromptDescriptor,
    block: suspend PromptScope.() -> List<PromptMessage>,
): PromptHandler {
    val coroutineName = CoroutineName("prompt:${descriptor.name()}")
    return PromptHandler { ctx: InteractionContext, request: PromptRequest ->
        runSuspendHandler(coroutineName) {
            PromptResult.messages(PromptScope(ctx, request).block())
        }
    }
}
