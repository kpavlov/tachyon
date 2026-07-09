// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.ReadResourceRequest

@TachyonDsl
public class ResourceScope
    internal constructor(
        public val ctx: InteractionContext,
        public val request: ReadResourceRequest,
    ) {
        public val uri: String
            get() = request.uri()
    }
