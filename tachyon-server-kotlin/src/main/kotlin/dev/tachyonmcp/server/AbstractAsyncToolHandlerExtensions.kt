/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server

import dev.tachyonmcp.server.features.tools.AbstractAsyncToolHandler
import dev.tachyonmcp.server.features.tools.ToolArgs
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.session.McpContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletionStage

@JvmSynthetic
internal fun asyncHandler(
    descriptor: ToolDescriptor,
    block: suspend ToolScope.() -> ToolResult<*>,
): AbstractAsyncToolHandler =
    object : AbstractAsyncToolHandler(descriptor) {
        @Volatile
        private var cachedDispatcher: CoroutineDispatcher? = null
        private val coroutineName = CoroutineName("tool:${descriptor.name()}")

        override fun handleAsync(
            context: McpContext,
            args: ToolArgs,
        ): CompletionStage<out ToolResult<*>> {
            val dispatcher =
                cachedDispatcher ?: context
                    .server()
                    .mcpServer()
                    .executor()
                    .asCoroutineDispatcher()
                    .also { cachedDispatcher = it }
            return CoroutineScope(dispatcher + coroutineName)
                .future { ToolScope(context, args).block() }
        }
    }
