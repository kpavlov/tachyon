// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.skill

import dev.tachyonmcp.server.ServerHandle
import dev.tachyonmcp.server.TachyonServer
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.domain.TextResourceContents
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.promptMessagesOf
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun createServer(port: Int = 0): ServerHandle = TachyonServer(port = port) {
    info {
        name = "demo-server"
        version = "1.0"
        description = "Demo MCP server"
    }
    capabilities {
        tools(listChanged = true)
        resources(subscribe = true, listChanged = true)
        prompts(listChanged = true)
    }
    session {
        stateless = false
        sessionTtl = 5.minutes
        shutdownGracePeriod = 5.seconds
    }
    network {
        allowedOrigins.add("*")
        allowNullOrigin = true
    }
    tool(name = "ping", description = "Simple ping") {
        ToolResult.text("pong")
    }
    resource(
        name = "config",
        uri = "demo://config",
        description = "Server configuration",
    ) {
        TextResourceContents.of(uri, "application/json", """{"mode":"production"}""")
    }
    prompt(name = "greet", description = "Generates a greeting") { _ ->
        promptMessagesOf(PromptMessage.user("Say hello"))
    }
}

fun addUserTemplate(handle: ServerHandle) {
    handle.server().resources().addTemplate(
        ResourceTemplateEntry.of(
            "user-profile",
            "demo://users/{userId}/profile",
            "User profile data",
            "application/json",
        ) { ctx, uri, params ->
            val userId = params["userId"]
            TextResourceContents.of(uri, "application/json", """{"userId":"$userId","name":"User"}""")
        },
    )
}

fun main() {
    val handle = createServer(8080)
    addUserTemplate(handle)
    println("MCP server on http://localhost:${handle.port()}/mcp")
}
