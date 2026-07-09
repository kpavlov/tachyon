/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server

import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolRequest
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.features.tools.toolHandler
import dev.tachyonmcp.server.session.DefaultMcpContext
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

internal class ToolHandlerFactoryTest {
    @Test
    fun `interrupt cancels coroutine`() {
        val started = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        val handler =
            toolHandler(
                ToolDescriptor.builder().name("interrupt-test").build(),
            ) {
                started.countDown()
                try {
                    delay(100500.seconds)
                } finally {
                    cancelled.set(true)
                }
                @Suppress("UNREACHABLE_CODE")
                ToolResult.text("never")
            }

        TachyonServer.builder().build().use { server ->
            val ctx = DefaultMcpContext.stateless(server)
            val request = ToolRequest.builder().name("interrupt-test").build()

            val thread =
                Thread.ofVirtual().start {
                    try {
                        handler.handle(ctx, request)
                    } catch (_: Exception) {
                        // expected — interrupt propagates
                    }
                }
            started.await(5, TimeUnit.SECONDS) shouldBe true
            thread.interrupt()
            thread.join(5_000)
            assertSoftly {
                cancelled.get() shouldBe true
            }
        }
    }

    @Test
    fun `handler is reusable after exception`() {
        val calls = AtomicInteger(0)
        val handler =
            toolHandler(
                ToolDescriptor.builder().name("supervisor-test").build(),
            ) {
                if (calls.incrementAndGet() == 1) {
                    error("boom")
                }
                ToolResult.text("ok")
            }

        TachyonServer.builder().build().use { server ->
            val ctx = DefaultMcpContext.stateless(server)
            val request = ToolRequest.builder().name("supervisor-test").build()

            val failure = shouldThrow<Exception> { handler.handle(ctx, request) }

            val second = handler.handle(ctx, request)

            assertSoftly {
                failure.message shouldBe "boom"
                second shouldBe ToolResult.text("ok")
                calls.get() shouldBe 2
            }
        }
    }
}
