/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.kotlin.server.features.resources

import dev.tachyonmcp.kotlin.server.config.TemplateScope
import dev.tachyonmcp.kotlin.server.features.runSuspendHandler
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.resources.ResourceHandler
import kotlinx.coroutines.CoroutineName

@JvmSynthetic
internal fun templateHandler(
    descriptor: dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor,
    block: suspend TemplateScope.() -> ResourceContents,
): dev.tachyonmcp.server.features.resources.ResourceHandler =
    templateHandler(descriptor.name(), descriptor.mimeType(), block)

@JvmSynthetic
internal fun templateHandler(
    name: String,
    mimeType: String?,
    block: suspend TemplateScope.() -> ResourceContents,
): ResourceHandler {
    val coroutineName = CoroutineName("resource-template:$name")
    return ResourceHandler {
        ctx,
        request,
        ->
        runSuspendHandler(coroutineName) {
            TemplateScope(ctx, request, mimeType).block()
        }
    }
}
