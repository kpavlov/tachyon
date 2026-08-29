// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.e2e

import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.server.features.tools.ToolResult.structured
import dev.tachyonmcp.e2e.mcp.AbstractStatelessMcpE2eTest
import dev.tachyonmcp.kotlin.server.TachyonServer
import dev.tachyonmcp.kotlin.server.domain.arguments
import dev.tachyonmcp.kotlin.server.json.KxSerializationSerde
import dev.tachyonmcp.testkit.Mcp20251125Client
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.equals.shouldEqual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

internal class KotlinE2eTest : AbstractStatelessMcpE2eTest<Mcp20251125Client>() {
    override fun createTestClient(): Mcp20251125Client = createTestClient(port)

    override fun createTestClient(port: Int): Mcp20251125Client = Mcp20251125Client(port)

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
        val sv =
            TachyonServer(port = 0) {
                json {
                    serde = KxSerializationSerde.Default
                }
                tool(
                    name = "greet",
                    description = "Typed greet tool",
                    inputSchema =
                        JsonSchema.unchecked(
                            //language=json
                            """
                            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
                            """.trimIndent(),
                        ),
                    outputSchema = JsonSchema.objectSchema(),
                ) {
                    val input = request.arguments<GreetArgs>()

                    structured(
                        GreetReply("${input.greeting}, ${input.name}!"),
                        "greeting response",
                    )
                }
            }
        port = sv.port()

        val client = createTestClient()
        client.initialize()
        val response =
            client.post(
                """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"greet","arguments":{"name":"World"}}}
                """.trimIndent(),
            )

        assert(response.statusCode() == 200) { "Expected 200, got ${response.statusCode()}" }
        val body = response.body()
        assert(body.contains("\"message\":\"Hello, World!\"")) { "Missing message in: $body" }
        assert(body.contains("\"text\":\"greeting response\"")) { "Missing text in: $body" }

        sv.close()
    }

    @Test
    fun `structured result without text emits serialized JSON text block`() {
        val sv =
            TachyonServer(port = 0) {
                json {
                    serde = KxSerializationSerde.Default
                }
                tool(
                    name = "greet",
                    description = "Typed greet tool",
                    inputSchema =
                        JsonSchema.unchecked( //language=json
                            """
                            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
                            """.trimIndent(),
                        ),
                    outputSchema = JsonSchema.objectSchema(),
                ) {
                    val input = request.arguments<GreetArgs>()

                    structured(GreetReply("${input.greeting}, ${input.name}!"))
                }
            }
        port = sv.port()

        val client = createTestClient()
        client.initialize()
        val response =
            client.post(
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

        sv.close()
    }

    @Test
    fun `decode with strict serde rejects unknown key as error`() {
        val sv =
            TachyonServer(port = 0) {
                json {
                    serde = KxSerializationSerde(Json { ignoreUnknownKeys = false })
                }
                tool(
                    name = "strict-greet",
                    description = "Strict typed greet tool",
                    inputSchema = JsonSchema.objectSchema(),
                    outputSchema = JsonSchema.objectSchema(),
                ) {
                    val input = request.arguments<GreetArgs>()

                    structured(
                        GreetReply("${input.greeting}, ${input.name}!"),
                        "greeting response",
                    )
                }
            }
        port = sv.port()

        val client = createTestClient()
        client.initialize()
        val response =
            client.post(
                // language=json
                """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"strict-greet","arguments":{"name":"World","unknownKey":"extra"}}}
                """.trimIndent(),
            )

        response.statusCode() shouldEqual 200
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

        sv.close()
    }
}
