// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.e2e

import dev.tachyonmcp.e2e.mcp.AbstractStatelessMcpE2eTest
import dev.tachyonmcp.kotlin.server.TachyonServer
import dev.tachyonmcp.kotlin.server.features.completions.CompletionResult
import dev.tachyonmcp.testkit.Mcp20251125Client
import io.kotest.matchers.equals.shouldEqual
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.junit.jupiter.api.Test

/**
 * Exercises the Kotlin `TachyonServer`'s post-build suspend registration —
 * `registerResource`/`registerResourceTemplate`/`registerPrompt`/`registerPromptCompletion`/
 * `registerResourceCompletion` — the Kotlin equivalents of the Java runtime server's
 * `server.resources().register(...)`, `server.prompts().register(...)`, and
 * `server.completions().registerForPrompt/Resource(...)`. Only `registerTool` had a Kotlin
 * post-build counterpart before; this proves the other four now round-trip over the wire too.
 */
internal class PostBuildRegistrationE2eTest : AbstractStatelessMcpE2eTest<Mcp20251125Client>() {
    override fun createTestClient(): Mcp20251125Client = createTestClient(port)

    override fun createTestClient(port: Int): Mcp20251125Client = Mcp20251125Client(port)

    @Test
    fun `registerResource adds a static resource to an already-built server`() {
        TachyonServer(port = 0) { }.use { server ->
            server.registerResource(name = "post-config", uri = "test://post-config") {
                TextResourceContents { text = "post-build" }
            }

            val client = createTestClient(server.port())
            client.initialize()
            val response =
                client.post("""{"jsonrpc":"2.0","id":2,"method":"resources/list","params":{}}""")

            response.statusCode() shouldEqual 200
            assertThatJson(response.body())
                .inPath("$.result.resources[0].uri")
                .isEqualTo("test://post-config")

            val readResponse =
                client.post(
                    """{"jsonrpc":"2.0","id":3,"method":"resources/read","params":{
                        "uri":"test://post-config"
                    }}""",
                )

            readResponse.statusCode() shouldEqual 200
            assertThatJson(readResponse.body())
                .inPath("$.result.contents[0].text")
                .isEqualTo("post-build")
        }
    }

    @Test
    fun `registerResourceTemplate adds a resource template to an already-built server`() {
        TachyonServer(port = 0) { }.use { server ->
            server.registerResourceTemplate(
                name = "post-item",
                uriTemplate = "test://post-items/{id}",
            ) {
                TextResourceContents { text = "item-${param("id")}" }
            }

            val client = createTestClient(server.port())
            client.initialize()
            val listResponse =
                client.post(
                    """{"jsonrpc":"2.0","id":2,"method":"resources/templates/list","params":{}}""",
                )
            assertThatJson(listResponse.body())
                .inPath("$.result.resourceTemplates[0].uriTemplate")
                .isEqualTo("test://post-items/{id}")

            val readResponse =
                client.post(
                    """{"jsonrpc":"2.0","id":3,"method":"resources/read","params":{
                        "uri":"test://post-items/42"
                    }}""",
                )
            assertThatJson(readResponse.body())
                .inPath("$.result.contents[0].text")
                .isEqualTo("item-42")
        }
    }

    @Test
    fun `registerPrompt adds a prompt to an already-built server`() {
        TachyonServer(port = 0) { }.use { server ->
            server.registerPrompt(name = "post-greet") {
                content { text("post-build greeting") }
            }

            val client = createTestClient(server.port())
            client.initialize()
            val getResponse =
                client.post(
                    """{"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{
                        "name":"post-greet"
                    }}""",
                )

            getResponse.statusCode() shouldEqual 200
            assertThatJson(getResponse.body())
                .inPath("$.result.messages[0].content.text")
                .isEqualTo("post-build greeting")
        }
    }

    @Test
    fun `registerPromptCompletion and registerResourceCompletion both work post-build`() {
        TachyonServer(port = 0) { }.use { server ->
            server.registerPrompt(name = "post-review", arguments = emptyList()) {
                content { text("review") }
            }
            server.registerPromptCompletion("post-review") {
                CompletionResult { values = listOf("kotlin", "java") }
            }
            server.registerResourceCompletion("test://post-items/{id}") {
                CompletionResult { values = listOf("$argumentValue-suggested") }
            }

            val client = createTestClient(server.port())
            client.initialize()

            val promptCompletion =
                client.post(
                    """{"jsonrpc":"2.0","id":2,"method":"completion/complete","params":{
                        "ref":{"type":"ref/prompt","name":"post-review"},
                        "argument":{"name":"language","value":"k"}
                    }}""",
                )
            assertThatJson(promptCompletion.body())
                .inPath("$.result.completion.values")
                .isArray
                .containsExactly("kotlin", "java")

            val resourceCompletion =
                client.post(
                    """{"jsonrpc":"2.0","id":3,"method":"completion/complete","params":{
                        "ref":{"type":"ref/resource","uri":"test://post-items/{id}"},
                        "argument":{"name":"id","value":"7"}
                    }}""",
                )
            assertThatJson(resourceCompletion.body())
                .inPath("$.result.completion.values")
                .isArray
                .containsExactly("7-suggested")
        }
    }
}
