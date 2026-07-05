// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.TachyonDsl

@TachyonDsl
public class TemplateScope
    internal constructor(
        public val ctx: InteractionContext,
        public val uri: String,
        public val params: Map<String, String>,
    ) {
        public fun param(name: String): String =
            params[name] ?: throw IllegalArgumentException("missing template variable: $name")
    }
