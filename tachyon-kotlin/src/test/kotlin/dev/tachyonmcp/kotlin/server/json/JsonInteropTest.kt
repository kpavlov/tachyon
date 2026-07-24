// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.json

import dev.tachyonmcp.server.json.JsonUtils
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

internal class JsonInteropTest {
    @Test
    fun `toJacksonNode converts all JSON kinds without a parse round-trip`() {
        val source =
            """
            {
              "string": "hello",
              "int": 42,
              "long": 9007199254740993,
              "double": 3.14,
              "bool": true,
              "nothing": null,
              "array": [1, "two", false, null, {"nested": "yes"}],
              "object": {"inner": {"deep": 1}}
            }
            """.trimIndent()

        val converted = Json.parseToJsonElement(source).toJacksonNode()

        val parsed = JsonUtils.parse(source)
        assertSoftly {
            converted shouldBe parsed
            converted.get("string").isString shouldBe true
            converted.get("long").asLong() shouldBe 9007199254740993L
            converted.get("double").asDouble() shouldBe 3.14
            converted.get("bool").asBoolean() shouldBe true
            converted.get("nothing").isNull shouldBe true
            converted.get("array").size() shouldBe 5
            converted
                .get("object")
                .get("inner")
                .get("deep")
                .asInt() shouldBe 1
        }
    }

    @Test
    fun `toJacksonNode preserves numeric-looking strings as text`() {
        val element = Json.parseToJsonElement("""{"version": "1.0", "count": 1.0}""")

        val node = element.toJacksonNode()

        assertSoftly {
            node.get("version").isString shouldBe true
            node.get("version").asString() shouldBe "1.0"
            node.get("count").isNumber shouldBe true
        }
    }

    @Test
    fun `toJacksonNodeMap converts each entry`() {
        val meta =
            mapOf(
                "a" to Json.parseToJsonElement("""{"x": 1}""") as JsonObject,
                "b" to Json.parseToJsonElement("""{"y": "z"}""") as JsonObject,
            )

        val converted = meta.toJacksonNodeMap()

        assertSoftly {
            converted.keys shouldBe setOf("a", "b")
            converted["a"] shouldBe JsonUtils.parse("""{"x": 1}""")
            converted["b"] shouldBe JsonUtils.parse("""{"y": "z"}""")
        }
    }
}
