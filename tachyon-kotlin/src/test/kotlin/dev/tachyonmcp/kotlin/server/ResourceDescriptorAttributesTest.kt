// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server

import dev.tachyonmcp.kotlin.server.domain.Icon
import dev.tachyonmcp.kotlin.server.features.resources.resourceDescriptor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * The flat `resource(...)` overload must accept the full optional attribute set of
 * [dev.tachyonmcp.api.server.features.resources.ResourceDescriptor.Builder].
 */
internal class ResourceDescriptorAttributesTest {
    @Test
    fun `every optional attribute reaches the descriptor`() {
        val icon = Icon { src = "https://example.com/resource.png" }

        buildServer {
            // when
            resource(
                name = "greeting",
                uri = "res://greeting",
                description = "desc",
                mimeType = "text/plain",
                title = "Title",
                size = 42,
                icons = listOf(icon),
                meta = mapOf("k" to "v"),
            ) { TextResourceContents { text = "hi" } }
        }.use { server ->
            // then
            val descriptor = server.resources().find("greeting").orElseThrow()
            descriptor.title() shouldBe "Title"
            descriptor.description() shouldBe "desc"
            descriptor.mimeType() shouldBe "text/plain"
            descriptor.size() shouldBe 42L
            descriptor.icons() shouldBe listOf(icon)
            descriptor.meta() shouldBe mapOf("k" to "v")
            descriptor shouldNotBe null
        }
    }

    @Test
    fun `extensionId is not a flat resource attribute, only settable via the descriptor scope`() {
        val descriptor = resourceDescriptor("greeting", "res://greeting") { extensionId = "ext" }

        descriptor.extensionId() shouldBe "ext"
    }
}
