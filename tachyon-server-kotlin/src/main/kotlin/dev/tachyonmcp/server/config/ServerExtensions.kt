// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.Server
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry

public fun Server.resourceTemplate(
    name: String,
    uriTemplate: String,
    description: String? = null,
    mimeType: String = "application/json",
    handler: TemplateScope.() -> ResourceContents,
) {
    resources()
        .addTemplate(
            ResourceTemplateEntry(
                name = name,
                uriTemplate = uriTemplate,
                description = description,
                mimeType = mimeType,
            ) { ctx, uri, params ->
                TemplateScope(ctx, uri, params).handler()
            },
        )
}
