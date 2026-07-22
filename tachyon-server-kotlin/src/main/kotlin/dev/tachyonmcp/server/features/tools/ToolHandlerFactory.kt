/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tools

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.config.ToolScope
import dev.tachyonmcp.server.features.runSuspendHandler
import kotlinx.coroutines.CoroutineName

/**
 * Wraps a suspend tool lambda into a blocking [AbstractToolHandler].
 * Cancellation is delivered via [Thread.interrupt] of the executing virtual thread,
 * which propagates through [kotlinx.coroutines.runBlocking] to cancel the coroutine.
 */
@JvmSynthetic
internal fun toolHandler(
    descriptor: ToolDescriptor,
    block: suspend ToolScope.() -> ToolResult,
): AbstractToolHandler {
    val coroutineName = CoroutineName("tool:${descriptor.name()}")
    return object : AbstractToolHandler(descriptor) {
        override fun handle(
            context: InteractionContext,
            request: ToolRequest,
        ): ToolResult =
            runSuspendHandler(coroutineName) {
                ToolScope(
                    ctx = context,
                    args = request.arguments(),
                    request = request,
                ).block()
            }
    }
}
