/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.kotlin.server.features.resources

import dev.tachyonmcp.kotlin.server.config.TemplateScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.resources.AsyncResourceHandler
import kotlinx.coroutines.CoroutineName

@JvmSynthetic
internal fun templateHandler(
    descriptor: dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor,
    runtime: CoroutineRuntime,
    block: suspend TemplateScope.() -> ResourceContents,
): AsyncResourceHandler = templateHandler(descriptor.name(), descriptor.mimeType(), runtime, block)

@JvmSynthetic
internal fun templateHandler(
    name: String,
    mimeType: String?,
    runtime: CoroutineRuntime,
    block: suspend TemplateScope.() -> ResourceContents,
): AsyncResourceHandler {
    val coroutineName = CoroutineName("resource-template:$name")
    return AsyncResourceHandler {
        ctx,
        request,
        ->
        runtime.future(coroutineName) {
            TemplateScope(ctx, request, mimeType).block()
        }
    }
}
