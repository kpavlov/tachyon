// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.features.completions.CompletionRequest

@TachyonDsl
public class CompletionScope
    internal constructor(
        public val ctx: InteractionContext,
        public val request: CompletionRequest,
    )
