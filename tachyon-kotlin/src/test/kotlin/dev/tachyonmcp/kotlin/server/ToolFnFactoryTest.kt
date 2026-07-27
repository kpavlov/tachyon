// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server

import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.kotlin.server.features.tools.ToolDescriptor
import dev.tachyonmcp.kotlin.server.features.tools.registerTool
import dev.tachyonmcp.kotlin.server.features.tools.toolFn
import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.TachyonServer
import dev.tachyonmcp.server.features.tasks.TaskSupport
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolRequest
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.internal.ServerEngine
import dev.tachyonmcp.server.session.DefaultDispatchContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class ToolFnFactoryTest {
    @Test
    fun `async handler returns while coroutine is suspended`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val handlerThread = AtomicReference<String>()

        withCoroutineRuntime { runtime, ctx ->
            val fn =
                toolFn("suspend-test", runtime) {
                    handlerThread.set(Thread.currentThread().name)
                    started.countDown()
                    release.await()
                    ToolResult.text("ok")
                }
            val request = ToolRequest.builder().name("suspend-test").build()

            val result = fn.apply(ctx, request).toCompletableFuture()

            started.await(5, TimeUnit.SECONDS) shouldBe true
            result.isDone shouldBe false
            release.countDown()
            result.get(5, TimeUnit.SECONDS) shouldBe ToolResult.text("ok")
            handlerThread.get().startsWith("kotlin-handler-") shouldBe true
        }
    }

    @Test
    fun `handler is reusable after exception`() {
        val calls = AtomicInteger()

        withCoroutineRuntime { runtime, ctx ->
            val fn =
                toolFn("supervisor-test", runtime) {
                    if (calls.incrementAndGet() == 1) {
                        error("boom")
                    }
                    ToolResult.text("ok")
                }
            val request = ToolRequest.builder().name("supervisor-test").build()

            val failure =
                shouldThrow<ExecutionException> {
                    fn.apply(ctx, request).toCompletableFuture().get(5, TimeUnit.SECONDS)
                }
            val second =
                fn.apply(ctx, request).toCompletableFuture().get(5, TimeUnit.SECONDS)

            failure.cause?.message shouldBe "boom"
            second shouldBe ToolResult.text("ok")
            calls.get() shouldBe 2
        }
    }

    @Test
    fun `server close cancels suspended handler`() {
        val runtime = CoroutineRuntime()
        val delegate =
            TachyonServer
                .builder()
                .extension(runtime)
                .build() as ServerEngine
        val started = CountDownLatch(1)
        val cancelled = AtomicBoolean()
        try {
            val fn =
                toolFn("shutdown-test", runtime) {
                    started.countDown()
                    try {
                        delay(100500.seconds)
                    } finally {
                        cancelled.set(true)
                    }
                    @Suppress("UNREACHABLE_CODE")
                    ToolResult.text("never")
                }
            val ctx = DefaultDispatchContext.stateless(delegate)

            fn.apply(
                ctx,
                ToolRequest.builder().name("shutdown-test").build(),
            )

            started.await(5, TimeUnit.SECONDS) shouldBe true
            delegate.close()
            cancelled.get() shouldBe true
        } finally {
            delegate.close()
        }
    }

    @Test
    fun `server close respects shutdown grace period`() {
        val runtime = CoroutineRuntime()
        val release = CompletableDeferred<Unit>()
        val started = CountDownLatch(1)
        Executors
            .newThreadPerTaskExecutor(
                Thread.ofVirtual().name("bounded-shutdown-", 0).factory(),
            ).use { executor ->
                val server =
                    TachyonServer
                        .builder()
                        .executor(executor)
                        .runtime { it.shutdownGracePeriod(Duration.ofMillis(50)) }
                        .extension(runtime)
                        .build() as ServerEngine
                val fn =
                    toolFn("bounded-shutdown", runtime) {
                        started.countDown()
                        withContext(NonCancellable) {
                            release.await()
                        }
                        ToolResult.text("done")
                    }

                fn.apply(
                    DefaultDispatchContext.stateless(server),
                    ToolRequest.builder().name("bounded-shutdown").build(),
                )
                try {
                    started.await(5, TimeUnit.SECONDS) shouldBe true

                    val before = System.nanoTime()
                    server.close()
                    val elapsed = Duration.ofNanos(System.nanoTime() - before)

                    (elapsed < Duration.ofSeconds(2)) shouldBe true
                } finally {
                    release.complete(Unit)
                    server.close()
                }
            }
    }

    @Test
    fun `task cancellation cancels coroutine`() {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val descriptor =
            ToolDescriptor
                .builder()
                .name("cancellable")
                .taskSupport(TaskSupport.OPTIONAL)
                .build()

        TachyonServer(port = 0) {
            name("cancellable-tool-test")
            session { enabled = true }
            tool(descriptor) {
                started.countDown()
                try {
                    awaitCancellation()
                } finally {
                    cancelled.countDown()
                }
            }
        }.use { server ->
            McpProbe(server.port()).use { probe ->
                probe.initialize()
                val call =
                    probe.post(
                        """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":""" +
                            """{"name":"cancellable","arguments":{},"task":{}}}""",
                    )
                val callJson =
                    call
                        .body()
                        .lineSequence()
                        .filter { it.startsWith("data:") }
                        .map { it.removePrefix("data:").trim() }
                        .first { it.contains(""""id":2""") }
                val taskId =
                    ObjectMapper()
                        .readTree(callJson)
                        .get("result")
                        .get("task")
                        .get("taskId")
                        .asString()

                started.await(5, TimeUnit.SECONDS) shouldBe true
                val cancel =
                    probe.post(
                        """{"jsonrpc":"2.0","id":3,"method":"tasks/cancel","params":{"taskId":"$taskId"}}""",
                    )

                cancel.body() shouldContain """"status":"cancelled""""
                cancelled.await(5, TimeUnit.SECONDS) shouldBe true
            }
        }
    }

    @Test
    fun `post-build tool uses server coroutine runtime`() {
        TachyonServer(port = 0) {
            name("dynamic-tool-test")
            session { enabled = true }
        }.use { server ->
            server.registerTool("dynamic") {
                delay(10.milliseconds)
                ToolResult.text("dynamic-ok")
            }

            McpProbe(server.port()).use { probe ->
                probe.initialize()
                probe.callTool("dynamic").body() shouldContain "dynamic-ok"
            }
        }
    }

    private fun withCoroutineRuntime(block: (CoroutineRuntime, InteractionContext) -> Unit) {
        val runtime = CoroutineRuntime()
        Executors
            .newThreadPerTaskExecutor(
                Thread.ofVirtual().name("kotlin-handler-", 0).factory(),
            ).use { executor ->
                val delegate =
                    TachyonServer
                        .builder()
                        .executor(executor)
                        .extension(runtime)
                        .build() as ServerEngine
                delegate.use {
                    block(runtime, DefaultDispatchContext.stateless(delegate))
                }
            }
    }
}
