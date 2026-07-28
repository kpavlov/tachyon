// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.resources

import dev.tachyonmcp.api.server.domain.ResourceContents
import dev.tachyonmcp.api.server.features.resources.AsyncResourceFn
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.kotlin.server.config.ResourceScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import kotlinx.coroutines.CoroutineName

/**
 * Wraps a suspend resource lambda into an [AsyncResourceFn].
 */
@JvmSynthetic
internal fun resourceFn(
    descriptor: ResourceDescriptor,
    runtime: CoroutineRuntime,
    block: suspend ResourceScope.() -> ResourceContents,
): AsyncResourceFn = resourceFn(descriptor.name(), descriptor.mimeType(), runtime, block)

@JvmSynthetic
internal fun resourceFn(
    name: String,
    mimeType: String?,
    runtime: CoroutineRuntime,
    block: suspend ResourceScope.() -> ResourceContents,
): AsyncResourceFn {
    val coroutineName = CoroutineName("resource:$name")
    return AsyncResourceFn {
        ctx,
        request,
        ->
        runtime.future(coroutineName) {
            ResourceScope(ctx, request, mimeType).block()
        }
    }
}
