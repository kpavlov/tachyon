// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.ServerBuilder
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.ReadResourceRequest
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.resources.ResourceDescriptor

@TachyonDsl
public class ResourceScope
    internal constructor(
        public val ctx: InteractionContext,
        public val request: ReadResourceRequest,
    ) {
        public val uri: String
            get() = request.uri()
    }

public fun ServerBuilder.resource(
    name: String,
    uri: String,
    description: String? = null,
    mimeType: String = "application/json",
    handler: ResourceScope.() -> ResourceContents,
): ServerBuilder =
    resource(ResourceDescriptor.of(name, uri, description, mimeType)) { ctx, req ->
        ResourceScope(ctx, req).handler()
    }
