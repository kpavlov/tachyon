/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server

import dev.tachyonmcp.server.features.tools.AbstractAsyncToolHandler
import dev.tachyonmcp.server.features.tools.ToolArgs
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.session.McpContext
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
        override fun handleAsync(
            context: McpContext,
            args: ToolArgs,
        ): CompletionStage<out ToolResult<*>> {
            val dispatcher =
                context
                    .server()
                    .mcpServer()
                    .executor()
                    .asCoroutineDispatcher()
            return CoroutineScope(dispatcher + CoroutineName("tool:${descriptor.name()}"))
                .future { ToolScope(context, args).block() }
        }
    }
