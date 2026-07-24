/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.kotlin.server.features.resources

import dev.tachyonmcp.kotlin.server.config.ResourceScope
import dev.tachyonmcp.kotlin.server.features.runSuspendHandler
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.resources.ResourceHandler
import kotlinx.coroutines.CoroutineName

/**
 * Wraps a suspend resource lambda into a [dev.tachyonmcp.server.features.resources.ResourceHandler].
 */
@JvmSynthetic
internal fun resourceHandler(
    descriptor: dev.tachyonmcp.server.features.resources.ResourceDescriptor,
    block: suspend ResourceScope.() -> ResourceContents,
): dev.tachyonmcp.server.features.resources.ResourceHandler =
    resourceHandler(descriptor.name(), descriptor.mimeType(), block)

@JvmSynthetic
internal fun resourceHandler(
    name: String,
    mimeType: String?,
    block: suspend ResourceScope.() -> ResourceContents,
): ResourceHandler {
    val coroutineName = CoroutineName("resource:$name")
    return ResourceHandler {
        ctx,
        request,
        ->
        runSuspendHandler(coroutineName) {
            ResourceScope(ctx, request, mimeType).block()
        }
    }
}
