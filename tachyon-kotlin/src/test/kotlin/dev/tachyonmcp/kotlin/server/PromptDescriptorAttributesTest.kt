// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server

import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.kotlin.server.domain.Icon
import dev.tachyonmcp.kotlin.server.domain.PromptArgument
import dev.tachyonmcp.kotlin.server.features.prompts.PromptDescriptor
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The flat `prompt(...)` overload must accept the full optional attribute set of
 * [dev.tachyonmcp.api.server.features.prompts.PromptDescriptor.Builder].
 */
internal class PromptDescriptorAttributesTest {
    @Test
    fun `every optional attribute reaches the descriptor`() {
        val icon = Icon { src = "https://example.com/prompt.png" }
        val argument =
            PromptArgument {
                name = "topic"
                required = true
            }

        buildServer {
            // when
            prompt(
                name = "summarize",
                description = "desc",
                title = "Title",
                arguments = listOf(argument),
                inputSchema = JsonSchema.objectSchema(),
                icons = listOf(icon),
                meta = mapOf("k" to "v"),
            ) { content { text("Summarize this") } }
        }.use { server ->
            // then
            val descriptor = server.prompts().find("summarize").orElseThrow()
            descriptor.title() shouldBe "Title"
            descriptor.description() shouldBe "desc"
            descriptor.arguments() shouldBe listOf(argument)
            descriptor.inputSchema() shouldBe JsonSchema.objectSchema()
            descriptor.icons() shouldBe listOf(icon)
            descriptor.meta() shouldBe mapOf("k" to "v")
        }
    }

    @Test
    fun `extensionId is not a flat prompt attribute, only settable via the descriptor scope`() {
        val descriptor =
            PromptDescriptor {
                name = "summarize"
                extensionId = "ext"
            }

        descriptor.extensionId() shouldBe "ext"
    }
}
