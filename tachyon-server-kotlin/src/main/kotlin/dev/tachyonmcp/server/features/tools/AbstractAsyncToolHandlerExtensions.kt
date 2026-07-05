/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.config.ToolScope
import dev.tachyonmcp.server.session.DispatchContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletionStage

@JvmSynthetic
internal fun asyncHandler(
    descriptor: ToolDescriptor,
    block: suspend ToolScope.() -> ToolResult,
): AbstractAsyncToolHandler =
    object : AbstractAsyncToolHandler(descriptor) {
        @Volatile
        private var cachedDispatcher: CoroutineDispatcher? = null
        private val coroutineName = CoroutineName("tool:${descriptor.name()}")

        override fun handleAsync(
            context: InteractionContext,
            args: ToolArgs,
        ): CompletionStage<out ToolResult> {
            val dispatcher =
                cachedDispatcher ?: (context as DispatchContext)
                    .server()
                    .executor()
                    .asCoroutineDispatcher()
                    .also { cachedDispatcher = it }
            return CoroutineScope(dispatcher + coroutineName)
                .future { ToolScope(context, args).block() }
        }
    }
