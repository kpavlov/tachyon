// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.skill

import dev.tachyonmcp.server.ServerHandle
import dev.tachyonmcp.server.TachyonServer
import dev.tachyonmcp.server.config.Mode
import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.domain.TextResourceContents
import dev.tachyonmcp.server.features.prompts.promptMessagesOf
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.json.KxSerializationSerde
import dev.tachyonmcp.server.json.NetworkntJsonSchemaValidator
import dev.tachyonmcp.transport.netty.NettyIoEngine
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun createServer(port: Int = 0): ServerHandle = TachyonServer(port = port) {
    // ── identity ──────────────────────────────────────────────
    info {
        name = "demo-server"
        title = "Demo MCP Server"
        version = "1.0"
        description = "Demo MCP server — shows all props"
        instructions = "Beep boop. I demo"
        websiteUrl = "https://example.com/mcp"
        icons += Icon(src = "https://example.com/icon.png", mimeType = "image/png", sizes = listOf("64x64"), theme = "white")
    }

    // ── capabilities — all switches + helpers ─────────────────
    capabilities {
        tools = Mode.ON
        toolsListChanged = true
        resources = true
        resourcesSubscribe = true
        resourcesListChanged = true
        prompts = true
        promptsListChanged = true
        tasks = true
        tasksCancel = true
        tasksRequests = true
        completions = true
        logging = true
        // helpers — same result shorter:
        // tools(listChanged = true)
        // resources(subscribe = true, listChanged = true)
        // prompts(listChanged = true)
    }

    // ── session — stateful or stateless ───────────────────────
    session {
        enabled = true
        sessionTtl = 5.minutes
        janitorInterval = 5.seconds
        sessionStore = null   // default: InMemorySessionStore
        sessionLogRouter = null // default: InMemorySessionLogRouter
        // lambda DSL
        sessionIdGenerator { it.headers().get("X-Tenant-Id") ?: "anon" }
        // or direct assignment:
        // sessionIdGenerator = { "sid_" + Uuid.random().toHexString() }
    }

    // ── runtime ───────────────────────────────────────────────
    runtime {
        shutdownGracePeriod = 7.seconds
    }

    // ── network — everything you can set ──────────────────────
    network {
        host = "0.0.0.0"
        // port = 0 // inherited from createServer(port)
        endpointPath = "/mcp"
        allowedOrigins.add("*")
        allowedHeaders.add("Authorization")
        allowNullOrigin = true
        allowPrivateNetworks = true
        readerIdleTimeout = 5.minutes
        writerIdleTimeout = 60.seconds
        heartbeatInterval = 15.seconds
        maxContentLength = 1_000_000
        ioEngine = NettyIoEngine.AUTO
    }

    // ── json — serde + schema validation ──────────────────────
    json {
        serde = KxSerializationSerde.Default            // default Jackson
        inputValidator = NetworkntJsonSchemaValidator() // default NetworkntJsonSchemaValidator
        outputValidator = inputValidator                // default = inputValidator
    }

    // ── tools ─────────────────────────────────────────────────
    tool(name = "ping", description = "Simple ping") {
        ToolResult.text("pong")
    }

    // ── resources ─────────────────────────────────────────────
    resource(
        name = "config",
        uri = "demo://config",
        description = "Server configuration",
        mimeType = "application/json",
    ) {
        TextResourceContents(uri = uri, mimeType = "application/json", text = """{"mode":"production"}""")
    }

    // ── prompts ───────────────────────────────────────────────
    prompt(name = "greet", description = "Generates a greeting") {
        promptMessagesOf(PromptMessage.user("Say hello"))
    }

    // ── netty pipeline customiser (escape hatch) ──────────────
    pipelineCustomizer { }
}

fun main() {
    val handle = createServer(8080)
    println("MCP server on http://localhost:${handle.port()}/mcp")
}
