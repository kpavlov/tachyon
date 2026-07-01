// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry
import dev.tachyonmcp.server.session.McpContext

@TachyonDsl
public class TemplateScope
    @PublishedApi
    internal constructor(
        public val ctx: McpContext,
        public val uri: String,
        public val params: Map<String, String>,
    ) {
        public fun param(name: String): String =
            params[name] ?: throw IllegalArgumentException("missing template variable: $name")
    }

public fun McpServer.resourceTemplate(
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
