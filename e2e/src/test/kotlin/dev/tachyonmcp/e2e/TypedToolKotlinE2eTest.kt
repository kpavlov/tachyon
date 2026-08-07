// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.e2e

import dev.tachyonmcp.api.server.features.tools.ToolResult.structured
import dev.tachyonmcp.kotlin.server.TachyonServer
import dev.tachyonmcp.kotlin.server.domain.arguments
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.equals.shouldEqual
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test

/**
 * Exercises the reified `typedTool<In, Out>(...)` DSL overload, backed here by
 * [KtSchemaJsonSchemaFactory] registered via `META-INF/services`. Split out from [KotlinE2eTest]
 * because this is the only test class in the module that needs the `kt-schema-generator-json-jvm`
 * test dependency wired in.
 */
internal class TypedToolKotlinE2eTest : AbstractStatelessMcpE2eTest() {
    @Serializable
    private data class GreetArgs(
        val name: String,
        val greeting: String = "Hello",
    )

    @Serializable
    private data class GreetReply(
        val message: String,
    )

    @Test
    fun `typedTool round-trips with schemas`() {
        TachyonServer(port = 0) {
            typedTool<GreetArgs, GreetReply>(
                name = "greet",
                description = "Typed greet tool",
            ) {
                val input = request.arguments<GreetArgs>()

                structured(
                    GreetReply("${input.greeting}, ${input.name}!"),
                    "greeting response",
                )
            }
        }.use { server ->

            val client = createTestClient(server.port())
            client.initialize()
            val response =
                client.post(
                    // The generated schema marks `greeting` required despite its Kotlin default —
                    // ReflectionClassJsonSchemaGenerator.Default doesn't treat default-valued
                    // properties as optional — so it must be supplied explicitly here.
                    """
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"greet","arguments":{"name":"World","greeting":"Hi"}}}
                    """.trimIndent(),
                )

            response.statusCode() shouldEqual 200
            val body = response.body()
            body shouldEqualJson
                """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "result": {
                    "content": [
                      {
                        "type": "text",
                        "text": "greeting response"
                      }
                    ],
                    "structuredContent": {
                      "message": "Hi, World!"
                    }
                  }
                }
                """.trimIndent()
        }
    }

    @Test
    fun `Schema rejects unknown property`() {
        // The generated schema sets additionalProperties=false, so an unrecognized argument is
        // rejected by JSON-Schema validation before the tool handler (and its serde) ever runs —
        // a different, earlier failure than kotlinx.serialization's own unknown-key rejection
        // (see KotlinE2eTest's `decode with strict serde rejects unknown key as error`).
        TachyonServer(port = 0) {
            typedTool<GreetArgs, GreetReply>(
                name = "strict-greet",
                description = "Strict typed greet tool",
            ) {
                val input = request.arguments<GreetArgs>()

                structured(
                    GreetReply("${input.greeting}, ${input.name}!"),
                    "greeting response",
                )
            }
        }.use { server ->

            val client = createTestClient(server.port())
            client.initialize()
            val response =
                client.post(
                    // language=json
                    """
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"strict-greet","arguments":{"name":"World","greeting":"Hi","unknownKey":"extra"}}}
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
                    "message": "property 'unknownKey' is not defined in the schema and the schema does not allow additional properties"
                  }
                }
                """.trimIndent()
        }
    }
}
