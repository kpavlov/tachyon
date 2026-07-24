// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.kotlin.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.features.prompts.PromptRequest
import dev.tachyonmcp.server.kotlin.TachyonDsl

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
