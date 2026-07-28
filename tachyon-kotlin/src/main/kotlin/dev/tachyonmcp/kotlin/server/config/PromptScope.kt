// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
@file:JvmSynthetic

package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.kotlin.server.TachyonDsl
import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.features.prompts.PromptRequest

@TachyonDsl
public class PromptScope
    internal constructor(
        public val ctx: InteractionContext,
        public val request: PromptRequest,
    ) {
        /**
         * Convenience access to the prompt arguments string or null.
         */
        public val arguments: String?
            get() = request.arguments()
    }

/**
 * Builds a list of user-role [PromptMessage]s from the content blocks collected in [block] — one
 * message per block. For explicit roles use [dev.tachyonmcp.kotlin.server.domain.PromptMessage].
 *
 * ```kotlin
 * content {
 *     text("Summarize this")
 *     image(data, "image/png")
 * }
 * ```
 */
public fun PromptScope.content(block: ContentScope.() -> Unit): List<PromptMessage> =
    ContentScope().apply(block).blocks.map { PromptMessage.user(it) }
