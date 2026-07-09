/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources

import dev.tachyonmcp.server.config.ResourceScope
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.runSuspendHandler
import kotlinx.coroutines.CoroutineName

/**
 * Wraps a suspend resource lambda into a [ResourceHandler].
 */
@JvmSynthetic
internal fun resourceHandler(
    descriptor: ResourceDescriptor,
    block: suspend ResourceScope.() -> ResourceContents,
): ResourceHandler {
    val coroutineName = CoroutineName("resource:${descriptor.name()}")
    return ResourceHandler { ctx, req ->
        runSuspendHandler(coroutineName) {
            ResourceScope(ctx, req).block()
        }
    }
}
