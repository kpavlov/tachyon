// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.domain

import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

        assertSoftly {
            annotations.audience() shouldBe listOf(Role.USER)
            annotations.priority() shouldBe 0.7

            icon.src() shouldBe "https://example.com/icon.svg"
            icon.mimeType() shouldBe "image/svg+xml"
            icon.sizes() shouldBe listOf("any")
            icon.theme() shouldBe "light"

            promptArgument.name() shouldBe "topic"
            promptArgument.title() shouldBe "Topic"
            promptArgument.description() shouldBe "Topic to discuss"
            promptArgument.required() shouldBe true

            toolAnnotations.title() shouldBe "Read"
            toolAnnotations.readOnlyHint() shouldBe true
            toolAnnotations.destructiveHint() shouldBe false
            toolAnnotations.idempotentHint() shouldBe true
            toolAnnotations.openWorldHint() shouldBe false
        }
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

        assertSoftly {
            image.data() shouldBe "AQID"
            image.mimeType() shouldBe "image/png"

            audio.data() shouldBe "AQID"
            audio.mimeType() shouldBe "audio/mpeg"

            textResource.uri() shouldBe "text://resource"
            textResource.text() shouldBe "hello"
            textResource.mimeType() shouldBe "text/plain"

            blobResource.uri() shouldBe "blob://resource"
            blobResource.blob() shouldBe "AQID"
            blobResource.mimeType() shouldBe "application/octet-stream"
        }
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

        assertSoftly {
            prompt.name() shouldBe "prompt"
            prompt.description() shouldBe "Prompt"
            prompt.arguments() shouldBe listOf(promptArgument)
            prompt.icons() shouldBe listOf(icon)

            resource.name() shouldBe "resource"
            resource.uri() shouldBe "resource://one"
            resource.description() shouldBe "Resource"
            resource.annotations() shouldBe annotations
            resource.icons() shouldBe listOf(icon)

            resourceTemplate.name() shouldBe "template"
            resourceTemplate.uriTemplate() shouldBe "resource://{id}"
            resourceTemplate.description() shouldBe "Template"
            resourceTemplate.annotations() shouldBe annotations
            resourceTemplate.icons() shouldBe listOf(icon)

            tool.name() shouldBe "tool"
            tool.description() shouldBe "Tool"
            tool.annotations() shouldBe toolAnnotations
            tool.icons() shouldBe listOf(icon)
        }
    }

    @Test
    fun `TextResourceContents without a scope requires an explicit uri`() {
        val failure =
            shouldThrow<IllegalArgumentException> {
                TextResourceContents { text = "hello" }
            }

        failure.message shouldContain "TextResourceContents.uri is required"
        failure.message shouldContain "TextResourceContents { }"
    }

    @Test
    fun `BlobResourceContents without a scope requires an explicit uri`() {
        val failure =
            shouldThrow<IllegalArgumentException> {
                BlobResourceContents { blob = "AQID" }
            }

        failure.message shouldContain "BlobResourceContents.uri is required"
        failure.message shouldContain "BlobResourceContents { }"
    }
}
