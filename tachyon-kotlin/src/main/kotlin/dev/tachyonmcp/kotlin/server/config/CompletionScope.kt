// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.kotlin.server.TachyonDsl
import dev.tachyonmcp.protocol.api.runtime.InteractionContext
import dev.tachyonmcp.protocol.api.server.features.completions.CompletionRequest

@TachyonDsl
public class CompletionScope
    internal constructor(
        public val ctx: InteractionContext,
        public val request: CompletionRequest,
    )
