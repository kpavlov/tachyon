// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.kotlin.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.features.completions.CompletionRequest
import dev.tachyonmcp.server.kotlin.TachyonDsl

@TachyonDsl
public class CompletionScope
    internal constructor(
        public val ctx: InteractionContext,
        public val request: CompletionRequest,
    )
