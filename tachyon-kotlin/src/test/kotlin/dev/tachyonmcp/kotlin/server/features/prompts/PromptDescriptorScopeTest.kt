// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.prompts

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class PromptDescriptorScopeTest {
    @Test
    fun `PromptDescriptor block omits arguments and icons defaults to empty lists`() {
        val descriptor = PromptDescriptor { name = "bare-prompt" }

        descriptor.arguments() shouldBe emptyList()
        descriptor.icons() shouldBe emptyList()
    }
}
