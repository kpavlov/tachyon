// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.resources

import dev.tachyonmcp.api.server.domain.ResourceContents
import dev.tachyonmcp.api.server.features.resources.AsyncResourceFn
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.kotlin.server.config.TemplateScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import kotlinx.coroutines.CoroutineName

@JvmSynthetic
internal fun templateFn(
    descriptor: ResourceTemplateDescriptor,
    runtime: CoroutineRuntime,
    block: suspend TemplateScope.() -> ResourceContents,
): AsyncResourceFn = templateFn(descriptor.name(), descriptor.mimeType(), runtime, block)

@JvmSynthetic
internal fun templateFn(
    name: String,
    mimeType: String?,
    runtime: CoroutineRuntime,
    block: suspend TemplateScope.() -> ResourceContents,
): AsyncResourceFn {
    val coroutineName = CoroutineName("resource-template:$name")
    return AsyncResourceFn {
        ctx,
        request,
        ->
        runtime.future(coroutineName) {
            TemplateScope(ctx, request, mimeType).block()
        }
    }
}
