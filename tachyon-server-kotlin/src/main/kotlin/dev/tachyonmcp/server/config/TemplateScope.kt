// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.config
import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.Server
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry

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

public fun Server.resourceTemplate(
    name: String,
    uriTemplate: String,
    description: String? = null,
    mimeType: String = "application/json",
    handler: TemplateScope.() -> ResourceContents,
) {
    resources()
        .addTemplate(
            ResourceTemplateEntry.of(name, uriTemplate, description, mimeType) { ctx, uri, params ->
                TemplateScope(ctx, uri, params).handler()
            },
        )
}
