// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.json

import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

/**
 * Verifies [KtSchemaJsonSchemaFactory] is discovered through [ServiceLoader] and that
 * `JsonSchema.from(Class, Class::class)` — the reified `typedTool` path — resolves a
 * kt-schema-generated schema. This is the dedicated `tachyon-kotlin-kt-schema` integration
 * artifact, so the provider must always be present once this module is on the classpath.
 */
internal class KtSchemaJsonSchemaFactoryTest {
    @Serializable
    @SerialName("Model")
    private data class Model(
        val name: String,
        val count: Int,
    )

    @Test
    fun `KtSchemaJsonSchemaFactory is registered via ServiceLoader`() {
        val factory =
            ServiceLoader
                .load(JsonSchemaFactory::class.java)
                .single {
                    it.sourceType() == Class::class.java
                }

        (factory is KtSchemaJsonSchemaFactory) shouldBe true
    }

    @Test
    fun `JsonSchema from Class resolves a kt-schema generated schema`() {
        val schema = JsonSchema.from(Model::class.java, Class::class.java)

        schema.json() shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Model",
              "type": "object",
              "properties": {
                "name": {
                  "type": "string"
                },
                "count": {
                  "type": "integer"
                }
              },
              "additionalProperties": false,
              "required": [
                "name",
                "count"
              ]
            }
            """.trimIndent()
    }
}
