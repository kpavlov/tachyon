/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources

import dev.tachyonmcp.server.config.TemplateScope
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.runSuspendHandler
import kotlinx.coroutines.CoroutineName

@JvmSynthetic
internal fun templateHandler(
    descriptor: ResourceTemplateDescriptor,
    block: suspend TemplateScope.() -> ResourceContents,
): ResourceHandler = templateHandler(descriptor.name(), descriptor.mimeType(), block)

@JvmSynthetic
internal fun templateHandler(
    name: String,
    mimeType: String?,
    block: suspend TemplateScope.() -> ResourceContents,
): ResourceHandler {
    val coroutineName = CoroutineName("resource-template:$name")
    return ResourceHandler { ctx, request ->
        runSuspendHandler(coroutineName) {
            TemplateScope(ctx, request, mimeType).block()
        }
    }
}
