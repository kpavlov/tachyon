// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.domain

import dev.tachyonmcp.api.json.JsonSchema
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class RequestFactoriesTest {
    @Test
    fun `FormInputRequest accepts a JsonSchema directly`() {
        val schema = JsonSchema.unchecked("""{"type":"object"}""")

        val request = FormInputRequest("Enter details", schema)

        request.message() shouldBe "Enter details"
        request.requestedSchema() shouldBe schema
    }

    @Test
    @Suppress("DEPRECATION")
    fun `FormInputRequest from Map builds an equivalent JsonSchema`() {
        val schemaMap = mapOf("type" to "string", "minLength" to 1)

        val request = FormInputRequest("Enter your name", schemaMap)

        request.message() shouldBe "Enter your name"
        request.requestedSchema().json() shouldBe """{"type":"string","minLength":1}"""
    }
}
