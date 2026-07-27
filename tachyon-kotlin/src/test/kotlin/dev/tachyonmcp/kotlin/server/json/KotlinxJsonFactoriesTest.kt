// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.json

import dev.tachyonmcp.protocol.api.json.JsonDocument
import dev.tachyonmcp.protocol.api.json.JsonSchema
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

internal class KotlinxJsonFactoriesTest {
    @Test
    fun `KotlinxJsonElementFactory reports JsonElement as source type`() {
        KotlinxJsonElementFactory.INSTANCE.sourceType() shouldBe JsonElement::class.java
    }

    @Test
    fun `KotlinxJsonElementFactory retains the same element instance for unwrap`() {
        val element = Json.parseToJsonElement("""{"type": "object"}""")

        val schema = KotlinxJsonElementFactory.INSTANCE.toJsonSchema(element)
        val document = KotlinxJsonElementFactory.INSTANCE.toJsonDocument(element)

        assertSoftly {
            schema.unwrap(JsonElement::class.java).get() shouldBeSameInstanceAs element
            document.unwrap(JsonElement::class.java).get() shouldBeSameInstanceAs element
            schema.json() shouldEqualJson element.toString()
        }
    }

    @Test
    fun `KotlinxJsonObjectFactory reports JsonObject as source type`() {
        KotlinxJsonObjectFactory.INSTANCE.sourceType() shouldBe JsonObject::class.java
    }

    @Test
    fun `KotlinxJsonObjectFactory retains the same object instance for unwrap`() {
        val obj = Json.parseToJsonElement("""{"a": 1}""") as JsonObject

        val schema = KotlinxJsonObjectFactory.INSTANCE.toJsonSchema(obj)
        val document = KotlinxJsonObjectFactory.INSTANCE.toJsonDocument(obj)

        assertSoftly {
            schema.unwrap(JsonObject::class.java).get() shouldBeSameInstanceAs obj
            document.unwrap(JsonObject::class.java).get() shouldBeSameInstanceAs obj
            document.json() shouldEqualJson obj.toString()
        }
    }

    @Test
    fun `JsonSchema and JsonDocument resolve JsonObject via the generic from entry point`() {
        val obj = Json.parseToJsonElement("""{"type": "object"}""") as JsonObject

        val schema: JsonSchema = JsonSchema.from(obj, JsonObject::class.java)
        val document: JsonDocument = JsonDocument.from(obj, JsonObject::class.java)

        assertSoftly {
            schema.unwrap(JsonObject::class.java).get() shouldBeSameInstanceAs obj
            document.unwrap(JsonObject::class.java).get() shouldBeSameInstanceAs obj
        }
    }
}
