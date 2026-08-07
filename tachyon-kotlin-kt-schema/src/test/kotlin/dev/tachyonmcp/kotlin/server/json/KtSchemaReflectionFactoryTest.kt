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
 * Verifies [KtSchemaReflectionFactory] is discovered through [ServiceLoader] and that
 * `JsonSchema.generated(Class)` — the reified `typedTool` path — resolves a kt-schema-generated
 * schema via the reflection factory whenever no build-time codegen resource exists. This is the
 * dedicated `tachyon-kotlin-kt-schema` integration artifact, so the provider must always be
 * present once this module is on the classpath, chained after tachyon-core's resource factory.
 */
internal class KtSchemaReflectionFactoryTest {
    @Serializable
    @SerialName("Model")
    private data class Model(
        val name: String,
        val count: Int,
    )

    @Test
    fun `KtSchemaReflectionFactory is chained after the resource factory via ServiceLoader`() {
        val factories =
            ServiceLoader
                .load(JsonSchemaFactory::class.java)
                .sortedBy { it.priority() }

        factories.map { it.javaClass.name }.filter { it.contains("KtSchema") } shouldBe
            listOf(
                "dev.tachyonmcp.core.server.json.KtSchemaResourceFactory",
                "dev.tachyonmcp.kotlin.server.json.KtSchemaReflectionFactory",
            )
    }

    @Test
    fun `JsonSchema generated from a class without a resource falls back to reflection`() {
        val schema = JsonSchema.generated(Model::class.java)

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
