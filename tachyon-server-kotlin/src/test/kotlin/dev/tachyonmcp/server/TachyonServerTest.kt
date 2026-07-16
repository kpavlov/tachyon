// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.config.Mode
import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.domain.TextContent
import dev.tachyonmcp.server.domain.TextResourceContents
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.internal.ServerEngine
import dev.tachyonmcp.server.session.InMemorySessionEventStore
import dev.tachyonmcp.server.session.InMemorySessionStore
import dev.tachyonmcp.server.session.SessionIdGenerator
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Suppress("LongMethod")
internal class TachyonServerTest {
    @Test
    fun `all DSL parameters`() {
        val appName = "tachyon-e2e"
        val expectedIcon =
            Icon(
                src = "https://example.com/s/icon.png",
                mimeType = "image/png",
                sizes = listOf("64x64"),
                theme = "white",
            )
        TachyonServer(port = 0) {
            info {
                name = appName
                title = "My Test MCP Server"
                icons += expectedIcon
                websiteUrl = "https://example.com/mcp"
                version = "2.0.0"
                description = "e2e test server"
                instructions = "ignore me"
            }
            capabilities {
                tools {
                    mode = Mode.ON
                    listChanged = true
                    pageSize = 20
                }
                resources {
                    mode = Mode.ON
                    subscribe = true
                    listChanged = true
                    pageSize = 21
                }
                prompts {
                    mode = Mode.ON
                    listChanged = true
                    pageSize = 22
                }
                tasks {
                    enabled = true
                    list = true
                    cancel = true
                    requests = true
                    pageSize = 23
                }
                completions = true
                logging = true
            }
            network {
                host = "127.0.0.1"
                port = 0
                endpointPath = "/mcp"
                allowedOrigins.add("*")
                allowNullOrigin = true
                allowPrivateNetworks = true
                readerIdleTimeout = 124.seconds
                writerIdleTimeout = 60.seconds
                maxContentLength = 1_000_000
            }
            session {
                enabled = true
                sessionTtl = 15.seconds
                sessionStore = InMemorySessionStore()
                sessionEventStore = InMemorySessionEventStore()
                sessionIdGenerator { it.headers().get("X-Tenant-Id") ?: "anon" }
            }
            monitoring {
                slowRequestLogging = true
                slowRequestThreshold = 15.seconds
            }
            pipelineCustomizer { }
            tool("ping", "Health check") { ToolResult.text("pong") }
            resource(
                "config",
                "file:///config.yaml",
                description = "App config",
                mimeType = "text/yaml",
            ) {
                TextResourceContents(uri = uri, mimeType = "text/yaml", text = "key: value")
            }
            prompt("greet", "Say hello") {
                listOf(PromptMessage.user(TextContent("Hello, ${arguments ?: "world"}!")))
            }
        }.use { handle ->
            (handle.port() > 0) shouldBe true
            handle.host() shouldBe "127.0.0.1"
            val config = handle.config()

            // identity
            with(config.identity) {
                name shouldBe appName
                title shouldBe "My Test MCP Server"
                version shouldBe "2.0.0"
                description shouldBe "e2e test server"
                instructions shouldBe "ignore me"
                websiteUrl shouldBe "https://example.com/mcp"
                icons shouldBe listOf(expectedIcon)
                websiteUrl shouldBe "https://example.com/mcp"
            }

            // capabilities
            with(config.capabilities()) {
                tools().mode() shouldBe Mode.ON
                tools().listChanged() shouldBe true
                tools().pageSize() shouldBe 20
                resources().mode() shouldBe Mode.ON
                resources().subscribe() shouldBe true
                resources().listChanged() shouldBe true
                resources().pageSize() shouldBe 21
                prompts().mode() shouldBe Mode.ON
                prompts().listChanged() shouldBe true
                prompts().pageSize() shouldBe 22
                tasks().enabled() shouldBe true
                tasks().list() shouldBe true
                tasks().cancel() shouldBe true
                tasks().requests() shouldBe true
                tasks().pageSize() shouldBe 23
                completions() shouldBe true
                logging() shouldBe true
            }

            // session
            config.session.enabled shouldBe true
            config.session.sessionTtl shouldBe 15.seconds.toJavaDuration()
            config.session.sessionIdGenerator shouldNotBe SessionIdGenerator.DEFAULT

            // network
            with(config.network) {
                host shouldBe "127.0.0.1"
                endpointPath shouldBe "/mcp"
                allowNullOrigin shouldBe true
                readerIdleTimeout shouldBe 124.seconds.toJavaDuration()
                writerIdleTimeout shouldBe 60.seconds.toJavaDuration()
                maxContentLength shouldBe 1_000_000
                allowPrivateNetworks shouldBe true
            }

            with(config.monitoring) {
                slowRequestLogging shouldBe true
                slowRequestThreshold shouldBe 15.seconds.toJavaDuration()
            }

            // registered features
            handle.tools().find("ping").orElse(null) shouldNotBe null
            val engine = handle as ServerEngine
            engine.prompts().find("greet").orElse(null) shouldNotBe null
            engine.resources().find("config").orElse(null) shouldNotBe null

            // MCP initialize
            McpProbe(handle.port()).use { probe ->
                val init = probe.initialize()
                init.statusCode() shouldBe 200
                init.body() shouldContain appName
                init.body() shouldContain "2.0.0"
            }
        }
    }

    @Test
    fun `buildServer without binding`() {
        val server =
            buildServer {
                name("kotlin-build")
                capabilities {
                    tools { mode = Mode.AUTO }
                }
                tool("build", "Build test") { ToolResult.text("built") }
            }
        server.tools().find("build").orElse(null) shouldNotBe null
        server.close()
    }

    @Test
    fun `network port set via DSL without factory port parameter`() {
        TachyonServer {
            name("dsl-port-test")
            session { enabled = true }
            network { port = 0 }
        }.use { handle ->
            (handle.port() > 0) shouldBe true
        }
    }

    @Test
    fun `name-based suspend resourceTemplate registers and lists template`() {
        val schema = """{"type":"object"}"""
        TachyonServer(port = 0) {
            name("template-test")
            session { enabled = true }
            tool("check", inputSchema = schema) { ToolResult.text("ok") }
            resourceTemplate(
                name = "user-profile",
                uriTemplate = "user://{userId}/profile",
                description = "User profile template",
                mimeType = "application/json",
            ) {
                TextResourceContents(
                    uri = uri,
                    mimeType = "application/json",
                    text = "{\"id\":\"${param("userId")}\"}",
                )
            }
        }.use { handle ->
            McpProbe(handle.port()).use { probe ->
                probe.initialize()
                val response = probe.request(2, "resources/templates/list")
                response.statusCode() shouldBe 200
                response.body() shouldContain "user-profile"
                response.body() shouldContain "user://{userId}/profile"
            }
        }
    }

    @Test
    fun `suspend tool with delay returns correct result`() {
        TachyonServer(port = 0) {
            name("delay-test")
            session { enabled = true }
            tool("slow", "Delayed") {
                delay(10.milliseconds)
                ToolResult.text("delayed-ok")
            }
        }.use { handle ->
            McpProbe(handle.port()).use { probe ->
                val init = probe.initialize()
                init.statusCode() shouldBe 200

                val response = probe.callTool("slow")
                response.statusCode() shouldBe 200
                response.body() shouldContain "delayed-ok"
            }
        }
    }

    @Test
    fun `tool with outputSchema appears in tools list`() {
        val schema = """{"type":"object"}"""
        val outputSchema = """{"type":"object","properties":{"result":{"type":"string"}}}"""
        TachyonServer(port = 0) {
            name("output-test")
            session { enabled = true }
            tool(
                "with-output",
                "Has output schema",
                inputSchema = schema,
                outputSchema = outputSchema,
            ) {
                ToolResult.text("done")
            }
        }.use { handle ->
            McpProbe(handle.port()).use { probe ->
                probe.initialize()
                val response = probe.request(2, "tools/list")
                response.body() shouldContain "with-output"
                response.body() shouldContain "outputSchema"
            }
        }
    }

    @Test
    fun `tool with string overload schema`() {
        val schema = """{"type":"object"}"""
        TachyonServer(port = 0) {
            name("string-schema-test")
            session { enabled = true }
            tool("string-schema", inputSchema = schema) {
                ToolResult.text("ok")
            }
        }.use { handle ->
            handle.tools().find("string-schema").orElse(null) shouldNotBe null
        }
    }

    @Test
    fun `sync tool body compiles and works with suspend signature`() {
        TachyonServer(port = 0) {
            name("sync-test")
            session { enabled = true }
            tool("ping", "Health check") { ToolResult.text("pong") }
        }.use { handle ->
            McpProbe(handle.port()).use { probe ->
                probe.initialize()
                val response = probe.callTool("ping")
                response.statusCode() shouldBe 200
                response.body() shouldContain "pong"
            }
        }
    }

    @Test
    fun `notification sent during suspend tool arrives on the request stream`() {
        TachyonServer(port = 0) {
            name("notify-test")
            session { enabled = true }
            tool("notify", "Notifies mid-run") {
                delay(10.milliseconds)
                ctx.notifications().info("notify-test", "mid-run-note")
                delay(10.milliseconds)
                ToolResult.text("notify-done")
            }
        }.use { handle ->
            McpProbe(handle.port()).use { probe ->
                probe.initialize()
                val response = probe.callTool("notify")
                response.statusCode() shouldBe 200
                response.headers().firstValue("content-type").orElse("") shouldContain
                    "text/event-stream"

                val body = response.body()
                body shouldContain "notifications/message"
                body shouldContain "mid-run-note"
                body shouldContain "notify-done"
                (body.indexOf("mid-run-note") < body.indexOf("notify-done")) shouldBe true
            }
        }
    }
}
