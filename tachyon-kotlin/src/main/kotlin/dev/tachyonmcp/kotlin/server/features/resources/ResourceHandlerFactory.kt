// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.resources

import dev.tachyonmcp.kotlin.server.config.ResourceScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.protocol.api.server.domain.ResourceContents
import dev.tachyonmcp.protocol.api.server.features.resources.AsyncResourceHandler
import dev.tachyonmcp.protocol.api.server.features.resources.ResourceDescriptor
import kotlinx.coroutines.CoroutineName

/**
 * Wraps a suspend resource lambda into a [dev.tachyonmcp.protocol.api.server.features.resources.ResourceHandler].
 */
@JvmSynthetic
internal fun resourceHandler(
    descriptor: ResourceDescriptor,
    runtime: CoroutineRuntime,
    block: suspend ResourceScope.() -> ResourceContents,
): AsyncResourceHandler = resourceHandler(descriptor.name(), descriptor.mimeType(), runtime, block)

@JvmSynthetic
internal fun resourceHandler(
    name: String,
    mimeType: String?,
    runtime: CoroutineRuntime,
    block: suspend ResourceScope.() -> ResourceContents,
): AsyncResourceHandler {
    val coroutineName = CoroutineName("resource:$name")
    return AsyncResourceHandler {
        ctx,
        request,
        ->
        runtime.future(coroutineName) {
            ResourceScope(ctx, request, mimeType).block()
        }
    }
}
