/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server

import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.session.DefaultMcpContext
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

internal class AsyncHandlerTest {
    @Test
    fun `cancelling future cancels coroutine`() {
        val started = CompletableDeferred<Unit>()
        val handler =
            asyncHandler(
                ToolDescriptor.builder("cancel-test").build(),
            ) {
                started.complete(Unit)
                while (true) {
                    Thread.sleep(100)
                }
                @Suppress("UNREACHABLE_CODE")
                ToolResult.text("never")
            }

        TachyonServer.builder().build().use { server ->
            val ctx = DefaultMcpContext.stateless(server)
            val future =
                handler.handleAsync(
                    ctx,
                    dev.tachyonmcp.server.features.tools.ToolArgs
                        .of(null),
                ) as CompletableFuture<out ToolResult<*>>

            runBlocking { started.await() }
            future.cancel(true)

            assertSoftly {
                future.isCancelled() shouldBe true
            }
        }
    }
}
