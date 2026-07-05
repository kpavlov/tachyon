/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.config.ToolScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.runBlocking

/**
 * Wraps a suspend tool lambda into a blocking [AbstractToolHandler].
 * Cancellation is delivered via [Thread.interrupt] of the executing virtual thread,
 * which propagates through [kotlinx.coroutines.runBlocking] to cancel the coroutine.
 */
@JvmSynthetic
internal fun toolHandler(
    descriptor: ToolDescriptor,
    block: suspend ToolScope.() -> ToolResult,
): AbstractToolHandler =
    object : AbstractToolHandler(descriptor) {
        private val coroutineName = CoroutineName("tool:${descriptor.name()}")

        override fun handle(
            context: InteractionContext,
            request: ToolRequest,
        ): ToolResult =
            runBlocking(coroutineName) {
                ToolScope(
                    context,
                    ToolArgs.of(request.arguments(), request.payloadDeserializer()),
                ).block()
            }
    }
