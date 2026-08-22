// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server

import dev.tachyonmcp.kotlin.server.domain.Icon
import dev.tachyonmcp.kotlin.server.features.resources.ResourceTemplateDescriptor
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The flat `resourceTemplate(...)` overload must accept the full optional attribute set of
 * [dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor.Builder].
 */
internal class ResourceTemplateDescriptorAttributesTest {
    @Test
    fun `every optional attribute reaches the descriptor`() {
        val icon = Icon { src = "https://example.com/template.png" }

        buildServer {
            // when
            resourceTemplate(
                name = "file",
                uriTemplate = "file:///{path}",
                description = "desc",
                mimeType = "text/plain",
                title = "Title",
                icons = listOf(icon),
                meta = mapOf("k" to "v"),
            ) { TextResourceContents { text = "contents" } }
        }.use { server ->
            // then
            val descriptor = server.resources().findTemplate("file").orElseThrow()
            descriptor.title() shouldBe "Title"
            descriptor.description() shouldBe "desc"
            descriptor.mimeType() shouldBe "text/plain"
            descriptor.icons() shouldBe listOf(icon)
            descriptor.meta() shouldBe mapOf("k" to "v")
        }
    }

    @Test
    fun `extensionId is not flat, only settable via the descriptor scope`() {
        val descriptor =
            ResourceTemplateDescriptor {
                name = "file"
                uriTemplate = "file:///{path}"
                extensionId = "ext"
            }

        descriptor.extensionId() shouldBe "ext"
    }
}
