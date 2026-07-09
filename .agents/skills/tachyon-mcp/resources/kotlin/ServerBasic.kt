// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.skill

import dev.tachyonmcp.server.TachyonServer
import dev.tachyonmcp.server.config.Mode
import dev.tachyonmcp.server.config.NetworkConfig
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
import kotlin.time.toKotlinDuration
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun createServer(port: Int = NetworkConfig.UNSET_PORT): TachyonServer =
    TachyonServer(port = port) {
        // ── identity ──────────────────────────────────────────────
        info {
            name = "demo-server"
            title = "Demo MCP Server"
            version = "1.0"
            description = "Demo MCP server — shows all props"
            instructions = "Beep boop. I demo"
            websiteUrl = "https://example.com/mcp"
            icons +=
                Icon(
                    src = "https://example.com/icon.png",
                    mimeType = "image/png",
                    sizes = listOf("64x64"),
                    theme = "white",
                )
        }

        // ── capabilities — all switches + helpers ─────────────────
        capabilities {
            tools = Mode.ON
            toolsListChanged = true
            resources = Mode.ON
            resourcesSubscribe = true
            resourcesListChanged = true
            prompts = Mode.ON
            promptsListChanged = true
            tasks = true
            tasksCancel = true
            tasksRequests = true
            completions = true
            logging = true
        }

        // ── session — stateful or stateless ───────────────────────
        session {
            enabled = true
            sessionTtl = 5.minutes
            janitorInterval = 5.seconds
            sessionStore = null // default: InMemorySessionStore
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
            host = NetworkConfig.DEFAULT_HOST
            // port = 0 // inherited from createServer(port)
            endpointPath = NetworkConfig.DEFAULT_ENDPOINT_PATH
            allowedOrigins.add("*")
            allowedHeaders.add("Authorization")
            allowNullOrigin = true
            allowPrivateNetworks = true
            readerIdleTimeout = NetworkConfig.DEFAULT_READER_IDLE_TIMEOUT.toKotlinDuration()
            writerIdleTimeout = NetworkConfig.DEFAULT_WRITER_IDLE_TIMEOUT.toKotlinDuration()
            heartbeatInterval = NetworkConfig.DEFAULT_HEARTBEAT_INTERVAL.toKotlinDuration()
            maxContentLength = NetworkConfig.DEFAULT_MAX_CONTENT_LENGTH
            ioEngine = NettyIoEngine.AUTO
        }

        // ── json — serde + schema validation ──────────────────────
        json {
            serde = KxSerializationSerde.Default // default Jackson
            inputValidator = NetworkntJsonSchemaValidator() // default NetworkntJsonSchemaValidator
            outputValidator = inputValidator // default = inputValidator
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
            TextResourceContents(
                uri = uri,
                mimeType = "application/json",
                text = """{"mode":"production"}""",
            )
        }

        // ── prompts ───────────────────────────────────────────────
        prompt(name = "greet", description = "Generates a greeting") {
            promptMessagesOf(PromptMessage.user("Say hello"))
        }

        // ── netty pipeline customiser (escape hatch) ──────────────
        pipelineCustomizer { }
    }

fun main() {
    val server = createServer(8080)
    println("MCP server on http://localhost:${server.port()}/mcp")
}
