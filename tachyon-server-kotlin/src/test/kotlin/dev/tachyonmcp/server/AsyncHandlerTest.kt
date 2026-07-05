/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server

import dev.tachyonmcp.server.features.tools.ToolArgs
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.features.tools.asyncHandler
import dev.tachyonmcp.server.session.DefaultMcpContext
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

internal class AsyncHandlerTest {
    @Test
    fun `cancelling future cancels coroutine`() {
        val started = CompletableDeferred<Unit>()
        val cancelled = AtomicBoolean(false)
        val handler =
            asyncHandler(
                ToolDescriptor.builder().name("cancel-test").build(),
            ) {
                started.complete(Unit)
                try {
                    delay(100500.minutes)
                } finally {
                    cancelled.set(true)
                }
                @Suppress("UNREACHABLE_CODE")
                ToolResult.text("never")
            }

        TachyonServer.builder().build().use { server ->
            val ctx = DefaultMcpContext.stateless(server)
            val future =
                handler.handleAsync(ctx, ToolArgs()) as CompletableFuture<out ToolResult>

            runBlocking { started.await() }
            future.cancel(true)

            await().untilTrue(cancelled)
            assertSoftly {
                cancelled.get() shouldBe true
                future.isCancelled() shouldBe true
            }
        }
    }
}
