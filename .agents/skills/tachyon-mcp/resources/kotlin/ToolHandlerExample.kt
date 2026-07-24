// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.skill

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.TachyonServer
import dev.tachyonmcp.server.domain.Args
import dev.tachyonmcp.server.features.tools.AbstractToolHandler
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.kotlin.server.buildServer
import dev.tachyonmcp.kotlin.server.config.TachyonServerBuilder
import dev.tachyonmcp.kotlin.server.config.success
import dev.tachyonmcp.kotlin.server.domain.decode
import dev.tachyonmcp.kotlin.server.features.tools.registerTool
import dev.tachyonmcp.kotlin.server.features.tools.toolDescriptor
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.time.Duration.Companion.seconds

private val MAPPER = ObjectMapper()
private val GREET_SCHEMA: JsonNode =
    MAPPER.readTree(
        """
    {"type":"object","properties":{"name":{"type":"string","description":"Name to greet"}},"required":["name"]}
""",
    )

@Serializable
data class GreetArgs(
    val name: String,
    val greeting: String = "Hello",
)

@Serializable
data class GreetReply(
    val message: String,
)

/**
 * Registers a simple hello tool via DSL.
 */
fun TachyonServerBuilder.registerHello() {
    tool(name = "hello", description = "Say hello") {
        ToolResult.text("Hello, world!")
    }
}

/**
 * Suspend handler with input schema and arguments.
 */
fun TachyonServerBuilder.registerGreeting() {
    tool(
        name = "greeting",
        description = "Generates a personalized greeting",
        inputSchema = GREET_SCHEMA,
    ) {
        val name = request.arguments().stringValue("name")
        ToolResult.text("Hello, $name!")
    }
}

/**
 * Typed handler using decode + success — honors the configured serde.
 */
fun TachyonServerBuilder.registerTypedGreeting() {
    tool(
        name = "typed-greeting",
        description = "Typed greet via configured serde",
        inputSchema = GREET_SCHEMA,
    ) {
        val input = request.arguments().decode<GreetArgs>()
        success(GreetReply("${input.greeting}, ${input.name}!"), "greeting response")
    }
}

/**
 * Keep-alive for a long-running DSL tool.
 *
 * `readerIdleTimeout` (default 60s) closes a connection with no inbound bytes; a client awaiting a
 * reply sends none, so a tool slower than the timeout is reaped mid-run. Emit an early SSE comment
 * via `ctx.notifications().comment(...)` — it upgrades the POST to `text/event-stream` and arms the
 * heartbeat, keeping the stream alive with no progress token. The DSL also exposes `request`, so a
 * handler may forward `request.progressToken()` when the client opted into progress.
 */
fun TachyonServerBuilder.registerSlowTask() {
    tool(name = "slow-task", description = "Long task kept alive via SSE comments") {
        repeat(10) { i ->
            ctx
                .notifications()
                .comment("step $i") // upgrades POST -> SSE, keeps the connection alive
            delay(1.seconds) // suspend handler — use delay(), not Thread.sleep
        }
        ToolResult.text("done")
    }
}

/**
 * Class-based — extends AbstractToolHandler.
 */
class GreetingTool :
    AbstractToolHandler(
        toolDescriptor("greeting") {
            title = "Greeting"
            description = "Generates a personalized greeting"
            inputSchema = GREET_SCHEMA
        },
    ) {
    override fun handle(
        ctx: InteractionContext,
        args: Args,
    ): ToolResult {
        val name = args.stringValue("name")
        return ToolResult.text("Hello, $name!")
    }
}

/**
 * Async class-based — extends AbstractToolHandler with handleAsync.
 */
class AsyncGreetingTool :
    AbstractToolHandler(
        toolDescriptor("async-greeting") {
            title = "Async Greeting"
            description = "Async personalized greeting"
            inputSchema = GREET_SCHEMA
        },
    ) {
    override fun handleAsync(
        ctx: InteractionContext,
        args: Args,
    ): CompletionStage<out ToolResult> =
        CompletableFuture.completedFuture(ToolResult.text("Hello, Async!"))
}

/** Build a server using the DSL and register tools post-build. */
fun buildWithPostRegistration(): TachyonServer {
    val server =
        buildServer {
            registerHello()
            registerGreeting()
            registerTypedGreeting()
            registerSlowTask()
            tool(name = "inline", description = "Registered inline") {
                ToolResult.text("inline result")
            }
        }
    server.tools().register(GreetingTool())
    server.tools().register(AsyncGreetingTool())
    server.registerTool(
        name = "post-register",
        description = "Added after build",
    ) {
        ToolResult.text("post-registered")
    }
    return server
}
