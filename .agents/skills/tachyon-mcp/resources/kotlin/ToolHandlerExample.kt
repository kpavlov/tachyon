// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.skill

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.Server
import dev.tachyonmcp.server.TachyonServerBuilder
import dev.tachyonmcp.server.buildServer
import dev.tachyonmcp.server.features.tools.AbstractAsyncToolHandler
import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler
import dev.tachyonmcp.server.features.tools.ToolArgs
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.registerTool
import dev.tachyonmcp.server.toolDescriptor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

private val MAPPER = ObjectMapper()
private val GREET_SCHEMA: JsonNode = MAPPER.readTree("""
    {"type":"object","properties":{"name":{"type":"string","description":"Name to greet"}},"required":["name"]}
""")

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
 * Class-based — extend AbstractSyncToolHandler.
 */
class GreetingTool : AbstractSyncToolHandler(
    toolDescriptor("greeting") {
        title = "Greeting"
        description = "Generates a personalized greeting"
        inputSchema = GREET_SCHEMA
    },
) {
    override fun handle(ctx: InteractionContext, args: ToolArgs): ToolResult {
        val name = args.string("name")
        return ToolResult.text("Hello, $name!")
    }
}

/**
 * Async class-based — extend AbstractAsyncToolHandler.
 */
class AsyncGreetingTool : AbstractAsyncToolHandler(
    toolDescriptor("async-greeting") {
        title = "Async Greeting"
        description = "Async personalized greeting"
        inputSchema = GREET_SCHEMA
    },
) {
    override fun handleAsync(ctx: InteractionContext, args: ToolArgs): CompletionStage<out ToolResult> {
        return CompletableFuture.completedFuture(ToolResult.text("Hello, Async!"))
    }
}

/** Build a server using the DSL and register tools post-build. */
fun buildWithPostRegistration(): Server {
    val server = buildServer {
        registerHello()
        registerGreeting()
        tool(name = "inline", description = "Registered inline") {
            ToolResult.text("inline result")
        }
    }
    server.registerTool(GreetingTool())
    server.registerTool(AsyncGreetingTool())
    server.registerTool(
        toolDescriptor("post-register") {
            description = "Added after build"
        },
    ) {
        ToolResult.text("post-registered")
    }
    return server
}
