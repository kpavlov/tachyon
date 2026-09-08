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
 * Exercises the Kotlin DSL's `promptCompletion`/`resourceCompletion` registration, the
 * `CompletionResult { }` builder, and `CompletionScope`'s `argumentName`/`argumentValue`
 * properties end-to-end over `completion/complete` — the Kotlin equivalent of the Java-side
 * coverage in `CompletionTest`, since no prior test called these two DSL entry points at all.
 */
internal class PromptResourceCompletionE2eTest : AbstractStatelessMcpE2eTest<Mcp20251125Client>() {
    override fun createTestClient(): Mcp20251125Client = createTestClient(port)

    override fun createTestClient(port: Int): Mcp20251125Client = Mcp20251125Client(port)

    @Test
    fun `promptCompletion registers a completion handler built with CompletionResult builder`() {
        TachyonServer(port = 0) {
            promptCompletion("code_review") {
                CompletionResult {
                    values = listOf("python", "pytorch", "pyside")
                    total = 10
                    hasMore = true
                }
            }
        }.use { server ->
            val client = createTestClient(server.port())
            client.initialize()
            val response =
                client.post(
                    """{"jsonrpc":"2.0","id":2,"method":"completion/complete","params":{
                        "ref":{"type":"ref/prompt","name":"code_review"},
                        "argument":{"name":"language","value":"py"}
                    }}""",
                )

            response.statusCode() shouldEqual 200
            assertThatJson(response.body())
                .inPath("$.result.completion.values")
                .isArray
                .containsExactly("python", "pytorch", "pyside")
            assertThatJson(response.body()).inPath("$.result.completion.total").isEqualTo(10)
            assertThatJson(response.body()).inPath("$.result.completion.hasMore").isEqualTo(true)
        }
    }

    @Test
    fun `resourceCompletion reads argumentName and argumentValue from CompletionScope`() {
        TachyonServer(port = 0) {
            resourceCompletion("file:///{path}") {
                val candidate = "$argumentName:$argumentValue.txt"
                CompletionResult { values = listOf(candidate) }
            }
        }.use { server ->
            val client = createTestClient(server.port())
            client.initialize()
            val response =
                client.post(
                    """{"jsonrpc":"2.0","id":2,"method":"completion/complete","params":{
                        "ref":{"type":"ref/resource","uri":"file:///{path}"},
                        "argument":{"name":"path","value":"note"}
                    }}""",
                )

            response.statusCode() shouldEqual 200
            assertThatJson(response.body())
                .inPath("$.result.completion.values")
                .isArray
                .containsExactly("path:note.txt")
        }
    }
}
