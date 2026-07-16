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
    name: String,
    block: suspend TemplateScope.() -> ResourceContents,
): ResourceHandler {
    val coroutineName = CoroutineName("resource-template:$name")
    return ResourceHandler { ctx, uri, params, uriTemplate ->
        runSuspendHandler(coroutineName) {
            TemplateScope(ctx, uri, params, requireNotNull(uriTemplate)).block()
        }
    }
}
