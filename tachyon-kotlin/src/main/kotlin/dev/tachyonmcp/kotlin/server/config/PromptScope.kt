// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.kotlin.server.TachyonDsl
import dev.tachyonmcp.runtime.InteractionContext
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
