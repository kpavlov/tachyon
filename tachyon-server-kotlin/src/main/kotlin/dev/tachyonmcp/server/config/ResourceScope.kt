// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.UriTemplateValue

@TachyonDsl
public class ResourceScope
    internal constructor(
        public val ctx: InteractionContext,
        public val uri: String,
        public val params: Map<String, UriTemplateValue>,
        public val uriTemplate: String?,
    )
