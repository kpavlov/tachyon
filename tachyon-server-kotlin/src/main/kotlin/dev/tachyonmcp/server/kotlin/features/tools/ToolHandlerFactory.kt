/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.kotlin.features.tools

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.features.tools.ToolFn
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.kotlin.config.ToolScope
import dev.tachyonmcp.server.kotlin.features.runSuspendHandler
import kotlinx.coroutines.CoroutineName

/**
 * Wraps a suspend tool lambda into a blocking [dev.tachyonmcp.server.features.tools.AbstractToolHandler].
 * Cancellation is delivered via [Thread.interrupt] of the executing virtual thread,
 * which propagates through [kotlinx.coroutines.runBlocking] to cancel the coroutine.
 */
@JvmSynthetic
internal fun toolHandler(
    descriptor: dev.tachyonmcp.server.features.tools.ToolDescriptor,
    block: suspend ToolScope.() -> dev.tachyonmcp.server.features.tools.ToolResult,
): dev.tachyonmcp.server.features.tools.AbstractToolHandler {
    val fn = toolFn(descriptor.name(), block)
    return object : dev.tachyonmcp.server.features.tools.AbstractToolHandler(descriptor) {
        override fun handle(
            context: InteractionContext,
            request: dev.tachyonmcp.server.features.tools.ToolRequest,
        ): dev.tachyonmcp.server.features.tools.ToolResult = fn.apply(context, request)
    }
}

@JvmSynthetic
internal fun toolFn(
    name: String,
    block: suspend ToolScope.() -> ToolResult,
): ToolFn {
    val coroutineName = CoroutineName("tool:$name")
    return ToolFn { context, request ->
        runSuspendHandler(coroutineName) {
            ToolScope(
                ctx = context,
                args = request.arguments(),
                request = request,
            ).block()
        }
    }
}
