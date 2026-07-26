/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.kotlin.server.features

import dev.tachyonmcp.server.extensions.ServerExtension
import dev.tachyonmcp.server.internal.ServerEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class CoroutineRuntime : ServerExtension {
    @Volatile
    private var scope: CoroutineScope? = null

    private var shutdownGraceNanos: Long = 0

    override fun extensionId(): String = "dev.tachyonmcp/kotlin-coroutines"

    override fun bootstrap(server: ServerEngine) {
        check(scope == null) { "Kotlin coroutine runtime already started" }
        shutdownGraceNanos =
            server
                .config()
                .runtime()
                .shutdownGracePeriod()
                .toNanos()
        scope =
            CoroutineScope(
                SupervisorJob() +
                    server.executor().asCoroutineDispatcher() +
                    CoroutineName("tachyon-kotlin"),
            )
    }

    @Suppress("TooGenericExceptionCaught")
    fun <T> future(
        coroutineName: CoroutineName,
        block: suspend () -> T,
    ): CompletionStage<T> {
        val currentScope = checkNotNull(scope) { "Kotlin coroutine runtime is not active" }
        val future = CompletableFuture<T>()
        val job =
            currentScope.launch(coroutineName) {
                try {
                    future.complete(block())
                } catch (cancellation: CancellationException) {
                    future.completeExceptionally(cancellation)
                    throw cancellation
                } catch (failure: Throwable) {
                    future.completeExceptionally(failure)
                }
            }
        job.invokeOnCompletion { failure ->
            if (failure != null) {
                future.completeExceptionally(failure)
            }
        }
        future.whenComplete { _, _ ->
            if (future.isCancelled) {
                job.cancel()
            }
        }
        return future
    }

    override fun shutdown() {
        val currentScope = scope ?: return
        scope = null
        val job = checkNotNull(currentScope.coroutineContext[Job])
        val stopped = CompletableFuture<Unit>()
        job.invokeOnCompletion { stopped.complete(Unit) }
        currentScope.cancel(CancellationException("Tachyon server is shutting down"))
        try {
            stopped.get(shutdownGraceNanos, TimeUnit.NANOSECONDS)
        } catch (_: TimeoutException) {
            logger.warn("Kotlin handlers did not stop within the server shutdown grace period")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(CoroutineRuntime::class.java)
    }
}
