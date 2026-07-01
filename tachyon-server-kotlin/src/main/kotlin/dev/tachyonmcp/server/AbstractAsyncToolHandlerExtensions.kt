/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server

import dev.tachyonmcp.server.features.tools.AbstractAsyncToolHandler
import dev.tachyonmcp.server.features.tools.ToolArgs
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.session.McpContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@JvmSynthetic
internal fun McpServer.asyncHandler(
    descriptor: ToolDescriptor,
    block: suspend ToolScope.() -> ToolResult<*>,
): AbstractAsyncToolHandler =
    object : AbstractAsyncToolHandler(descriptor) {
        private val dispatcher = this@asyncHandler.executor().asCoroutineDispatcher()

        override fun handleAsync(
            context: McpContext,
            args: ToolArgs,
        ): CompletionStage<out ToolResult<*>> {
            val future = CompletableFuture<ToolResult<*>>()
            val scope = ToolScope(context, args)
            CoroutineScope(dispatcher).launch {
                try {
                    future.complete(block(scope))
                } catch (_: CancellationException) {
                    future.cancel(false)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
            return future
        }
    }
