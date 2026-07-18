/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e

import dev.tachyonmcp.server.domain.decode
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.json.KxSerializationSerde
import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

internal class KotlinE2eTest : AbstractMcpE2eTest() {
    @Serializable
    data class GreetArgs(
        val name: String,
        val greeting: String = "Hello",
    )

    @Serializable
    data class GreetReply(
        val message: String,
    )

    @Test
    fun `decode and ToolResult-of round-trip via configured serde`() {
        startServer {
            it
                .json { j ->
                    j.serde(KxSerializationSerde.Default)
                }.tool(
                    "greet",
                    "Typed greet tool",
                    //language=json
                    """
                    {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
                    """.trimIndent(),
                    // language=json
                    """{"type":"object"}""",
                ) { _, args ->
                    val input = args.decode<GreetArgs>()
                    ToolResult.of(
                        GreetReply("${input.greeting}, ${input.name}!"),
                        "greeting response",
                    )
                }
        }

        val client = createTestClient()
        val sessionId = client.initialize()
        val response =
            client.post(
                sessionId,
                """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"greet","arguments":{"name":"World"}}}
                """.trimIndent(),
            )

        assert(response.statusCode() == 200) { "Expected 200, got ${response.statusCode()}" }
        val body = response.body()
        assert(body.contains("\"message\":\"Hello, World!\"")) { "Missing message in: $body" }
        assert(body.contains("\"text\":\"greeting response\"")) { "Missing text in: $body" }
    }

    @Test
    fun `structured result without text emits serialized JSON text block`() {
        startServer {
            it
                .json { j ->
                    j.serde(KxSerializationSerde.Default)
                }.tool(
                    "greet",
                    "Typed greet tool",
                    //language=json
                    """
                    {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
                    """.trimIndent(),
                    // language=json
                    """{"type":"object"}""",
                ) { _, args ->
                    val input = args.decode<GreetArgs>()
                    ToolResult.of(GreetReply("${input.greeting}, ${input.name}!"))
                }
        }

        val client = createTestClient()
        val sessionId = client.initialize()
        val response =
            client.post(
                sessionId,
                """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"greet","arguments":{"name":"World"}}}
                """.trimIndent(),
            )

        assert(response.statusCode() == 200) { "Expected 200, got ${response.statusCode()}" }
        val body = response.body()
        // structuredContent carries the object (unescaped)
        assert(
            body.contains("\"message\":\"Hello, World!\""),
        ) { "Missing structuredContent in: $body" }
        // MCP backwards-compat: the serialized JSON is also injected as a text block, where the
        // object's quotes are escaped inside the text string value.
        assert(body.contains("{\\\"message\\\":\\\"Hello, World!\\\"}")) {
            "Missing serialized-JSON text block in: $body"
        }
    }

    @Test
    fun `decode with strict serde rejects unknown key as error`() {
        startServer {
            it
                .json { j ->
                    j.serde(KxSerializationSerde(Json { ignoreUnknownKeys = false }))
                }.tool(
                    "strict-greet",
                    "Strict typed greet tool",
                    """{"type":"object"}""",
                    """{"type":"object"}""",
                ) { _, args ->
                    val input = args.decode<GreetArgs>()
                    ToolResult.of(
                        GreetReply("${input.greeting}, ${input.name}!"),
                        "greeting response",
                    )
                }
        }

        val client = createTestClient()
        val sessionId = client.initialize()
        val response =
            client.post(
                sessionId,
                // language=json
                """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"strict-greet","arguments":{"name":"World","unknownKey":"extra"}}}
                """.trimIndent(),
            )

        assert(response.statusCode() == 200) { "Expected 200, got ${response.statusCode()}" }
        response.body() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "error": {
                "code": -32602,
                "message": "invalid argument 'arguments': could not be decoded: Encountered an unknown key 'unknownKey' at path: $"
              }
            }
            """.trimIndent()
    }
}
