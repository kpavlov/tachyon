// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class StructuredFactoryBuildersTest {
    @Test
    fun `Annotations block omits audience defaults to empty list`() {
        val annotations = Annotations { priority = 0.5 }

        annotations.audience() shouldBe emptyList()
    }

    @Test
    fun `Icon block omits sizes defaults to empty list`() {
        val icon = Icon { src = "https://example.test/icon.png" }

        icon.sizes() shouldBe emptyList()
    }
}
