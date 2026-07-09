// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.skill

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.Server
import dev.tachyonmcp.server.buildServer
import dev.tachyonmcp.server.config.TachyonServerBuilder
import dev.tachyonmcp.server.config.success
import dev.tachyonmcp.server.features.tools.ToolArgs
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolHandler
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.features.tools.decode
import dev.tachyonmcp.server.features.tools.registerTool
import dev.tachyonmcp.server.features.tools.toolDescriptor
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

private val MAPPER = ObjectMapper()
private val GREET_SCHEMA: JsonNode = MAPPER.readTree("""
    {"type":"object","properties":{"name":{"type":"string","description":"Name to greet"}},"required":["name"]}
""")

@Serializable
data class GreetArgs(val name: String, val greeting: String = "Hello")

@Serializable
data class GreetReply(val message: String)

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
    tool(name = "greeting", description = "Generates a personalized greeting", inputSchema = GREET_SCHEMA) {
        val name = args.string("name")
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
        val input = args.decode<GreetArgs>()
        success(GreetReply("${input.greeting}, ${input.name}!"), "greeting response")
    }
}

/**
 * Keep-alive for a long-running DSL tool.
 *
 * `readerIdleTimeout` (default 60s) closes a connection with no inbound bytes; a client awaiting a
 * reply sends none, so a tool slower than the timeout is reaped mid-run. Emit an early SSE comment
 * via `ctx.notifications().comment(...)` — it upgrades the POST to `text/event-stream` and arms the
 * heartbeat, keeping the stream alive with no progress token. `comment(...)` is the natural fit for
 * the DSL, whose [ToolScope] exposes `ctx` + `args` but not the request's progress token.
 */
fun TachyonServerBuilder.registerSlowTask() {
    tool(name = "slow-task", description = "Long task kept alive via SSE comments") {
        repeat(10) { i ->
            ctx.notifications().comment("step $i") // upgrades POST -> SSE, keeps the connection alive
            delay(1_000) // suspend handler — use delay(), not Thread.sleep
        }
        ToolResult.text("done")
    }
}

/**
 * Class-based — implements ToolHandler.
 */
class GreetingTool : ToolHandler {
    override fun descriptor() = toolDescriptor("greeting") {
        title = "Greeting"
        description = "Generates a personalized greeting"
        inputSchema = GREET_SCHEMA
    }

    override fun handle(ctx: InteractionContext, args: ToolArgs): ToolResult {
        val name = args.string("name")
        return ToolResult.text("Hello, $name!")
    }
}

/**
 * Async class-based — implements ToolHandler with handleAsync.
 */
class AsyncGreetingTool : ToolHandler {
    override fun descriptor() = toolDescriptor("async-greeting") {
        title = "Async Greeting"
        description = "Async personalized greeting"
        inputSchema = GREET_SCHEMA
    }

    override fun handleAsync(ctx: InteractionContext, args: ToolArgs): CompletionStage<out ToolResult> {
        return CompletableFuture.completedFuture(ToolResult.text("Hello, Async!"))
    }
}

/** Build a server using the DSL and register tools post-build. */
fun buildWithPostRegistration(): Server {
    val server = buildServer {
        registerHello()
        registerGreeting()
        registerTypedGreeting()
        registerSlowTask()
        tool(name = "inline", description = "Registered inline") {
            ToolResult.text("inline result")
        }
    }
    server.registerTool(GreetingTool())
    server.registerTool(AsyncGreetingTool())
    server.registerTool(
        name = "post-register",
        description = "Added after build",
    ) {
        ToolResult.text("post-registered")
    }
    return server
}
