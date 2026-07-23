// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.domain

import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class StructuredFactoryDslTest {
    @Test
    fun `metadata objects use receiver factories`() {
        val annotations =
            Annotations {
                audience = listOf(Role.USER)
                priority = 0.7
            }
        val icon =
            Icon {
                src = "https://example.com/icon.svg"
                mimeType = "image/svg+xml"
                sizes = listOf("any")
                theme = "light"
            }
        val promptArgument =
            PromptArgument {
                name = "topic"
                title = "Topic"
                description = "Topic to discuss"
                required = true
            }
        val toolAnnotations =
            ToolAnnotations {
                title = "Read"
                readOnlyHint = true
                destructiveHint = false
                idempotentHint = true
                openWorldHint = false
            }

        annotations.priority() shouldBe 0.7
        icon.src() shouldBe "https://example.com/icon.svg"
        promptArgument.required() shouldBe true
        toolAnnotations.readOnlyHint() shouldBe true
    }

    @Test
    fun `content objects use receiver factories`() {
        val image =
            ImageContent {
                data = "AQID"
                mimeType = "image/png"
            }
        val audio =
            AudioContent {
                data = "AQID"
                mimeType = "audio/mpeg"
            }
        val textResource =
            TextResourceContents {
                uri = "text://resource"
                text = "hello"
                mimeType = "text/plain"
            }
        val blobResource =
            BlobResourceContents {
                uri = "blob://resource"
                blob = "AQID"
                mimeType = "application/octet-stream"
            }

        image.mimeType() shouldBe "image/png"
        audio.mimeType() shouldBe "audio/mpeg"
        textResource.uri() shouldBe "text://resource"
        blobResource.uri() shouldBe "blob://resource"
    }

    @Test
    fun `descriptor objects use receiver factories`() {
        val icon =
            Icon {
                src = "https://example.com/icon.svg"
            }
        val annotations =
            Annotations {
                priority = 0.7
            }
        val promptArgument =
            PromptArgument {
                name = "topic"
            }
        val toolAnnotations =
            ToolAnnotations {
                readOnlyHint = true
            }
        val prompt =
            PromptDescriptor {
                name = "prompt"
                description = "Prompt"
                arguments = listOf(promptArgument)
                icons = listOf(icon)
            }
        val resource =
            ResourceDescriptor {
                name = "resource"
                uri = "resource://one"
                description = "Resource"
                this.annotations = annotations
                icons = listOf(icon)
            }
        val resourceTemplate =
            ResourceTemplateDescriptor {
                name = "template"
                uriTemplate = "resource://{id}"
                description = "Template"
                this.annotations = annotations
                icons = listOf(icon)
            }
        val tool =
            ToolDescriptor {
                name = "tool"
                description = "Tool"
                this.annotations = toolAnnotations
                icons = listOf(icon)
            }

        prompt.name() shouldBe "prompt"
        resource.name() shouldBe "resource"
        resourceTemplate.name() shouldBe "template"
        tool.name() shouldBe "tool"
    }
}
