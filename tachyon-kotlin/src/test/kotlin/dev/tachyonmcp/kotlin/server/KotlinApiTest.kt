/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.kotlin.server

import dev.tachyonmcp.kotlin.server.config.ToolScope
import dev.tachyonmcp.kotlin.server.config.success
import dev.tachyonmcp.kotlin.server.domain.arrayOrNull
import dev.tachyonmcp.kotlin.server.domain.boolean
import dev.tachyonmcp.kotlin.server.domain.booleanOrNull
import dev.tachyonmcp.kotlin.server.domain.decimalOrNull
import dev.tachyonmcp.kotlin.server.domain.decode
import dev.tachyonmcp.kotlin.server.domain.double
import dev.tachyonmcp.kotlin.server.domain.doubleOrNull
import dev.tachyonmcp.kotlin.server.domain.int
import dev.tachyonmcp.kotlin.server.domain.intOrNull
import dev.tachyonmcp.kotlin.server.domain.long
import dev.tachyonmcp.kotlin.server.domain.longOrNull
import dev.tachyonmcp.kotlin.server.domain.objectOrNull
import dev.tachyonmcp.kotlin.server.domain.string
import dev.tachyonmcp.kotlin.server.domain.stringOrNull
import dev.tachyonmcp.kotlin.server.domain.valuesAs
import dev.tachyonmcp.kotlin.server.json.KxSerializationSerde
import dev.tachyonmcp.kotlin.server.json.toJsonNode
import dev.tachyonmcp.server.domain.Args
import dev.tachyonmcp.server.domain.InvalidArgumentException
import dev.tachyonmcp.server.domain.TextContent
import dev.tachyonmcp.server.features.tools.ToolRequest
import dev.tachyonmcp.server.features.tools.ToolResult
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import dev.tachyonmcp.server.json.JsonSchema as JavaJsonSchema

internal class KotlinApiTest {
    // region: Overload resolution — all shapes compile
    // These tests verify overloads compile for all schema types

    @Test
    fun `JsonSchema overload registers a tool with input and output schema`() {
        val schema = JavaJsonSchema.objectSchema()
        TachyonServer(
            port = 0,
            {
                name("test")
                tool("t1", inputSchema = schema, outputSchema = schema) { ToolResult.text("ok") }
            },
        ).use { handle ->
            handle.tools().find("t1").orElse(null) shouldNotBe null
        }
    }

    @Test
    fun `String overload registers a tool with input and output schema`() {
        val json = """{"type":"object"}"""
        TachyonServer(
            port = 0,
            {
                name("test")
                tool("t2", inputSchema = json, outputSchema = json) { ToolResult.text("ok") }
            },
        ).use { handle ->
            handle.tools().find("t2").orElse(null) shouldNotBe null
        }
    }

    @Test
    fun `JsonObject overload registers a tool with input and output schema`() {
        val schema = buildJsonObject { put("type", "object") }
        TachyonServer(
            port = 0,
            {
                name("test")
                tool("t3", inputSchema = schema, outputSchema = schema) { ToolResult.text("ok") }
            },
        ).use { handle ->
            handle.tools().find("t3").orElse(null) shouldNotBe null
        }
    }

    // endregion

    // region: String.toJsonNode parse

    @Test
    fun `toJsonNode parses valid JSON`() {
        val node = """{"type":"object"}""".toJsonNode()
        node.isObject shouldBe true
        @Suppress("DEPRECATION")
        val type = node.get("type").asText()
        type shouldBe "object"
    }

    @Test
    fun `toJsonNode parses array JSON`() {
        val node = """[1,2,3]""".toJsonNode()
        node.isArray shouldBe true
        node.size() shouldBe 3
    }

    // endregion

    // region: Args orNull sugar

    @Test
    fun `orNull accessors return the value when the key is present`() {
        val args =
            Args.of(
                mapOf(
                    "str" to "v",
                    "int" to 42,
                    "long" to Long.MAX_VALUE,
                    "bool" to true,
                    "double" to 3.14,
                    "decimal" to BigDecimal("1.25"),
                    "obj" to mapOf("k" to "v"),
                    "arr" to listOf("x", "y"),
                ),
                null,
            )

        assertSoftly {
            args.stringOrNull("str") shouldBe "v"
            args.intOrNull("int") shouldBe 42
            args.longOrNull("long") shouldBe Long.MAX_VALUE
            args.booleanOrNull("bool") shouldBe true
            args.doubleOrNull("double") shouldBe 3.14
            args.decimalOrNull("decimal") shouldBe BigDecimal("1.25")
            args.objectOrNull("obj")?.stringValue("k") shouldBe "v"
            args.arrayOrNull("arr")?.valuesAs<String>() shouldBe listOf("x", "y")
        }
    }

    @Test
    fun `orNull accessors return null when the key is missing or JSON null`() {
        val args = Args.of(null, null)
        val nullArgs =
            Args.of(
                mapOf<String, Any?>(
                    "str" to null,
                    "int" to null,
                    "long" to null,
                    "bool" to null,
                    "double" to null,
                    "decimal" to null,
                    "obj" to null,
                    "arr" to null,
                ),
                null,
            )

        assertSoftly {
            args.stringOrNull("k") shouldBe null
            args.intOrNull("k") shouldBe null
            args.longOrNull("k") shouldBe null
            args.booleanOrNull("k") shouldBe null
            args.doubleOrNull("k") shouldBe null
            args.decimalOrNull("k") shouldBe null
            args.objectOrNull("k") shouldBe null
            args.arrayOrNull("k") shouldBe null
            nullArgs.stringOrNull("str") shouldBe null
            nullArgs.intOrNull("int") shouldBe null
            nullArgs.longOrNull("long") shouldBe null
            nullArgs.booleanOrNull("bool") shouldBe null
            nullArgs.doubleOrNull("double") shouldBe null
            nullArgs.decimalOrNull("decimal") shouldBe null
            nullArgs.objectOrNull("obj") shouldBe null
            nullArgs.arrayOrNull("arr") shouldBe null
        }
    }

    // endregion

    @Test
    fun `accessors with default return the value when the key is present`() {
        val args =
            Args.of(
                mapOf(
                    "str" to "v",
                    "bool" to true,
                    "int" to 42,
                    "long" to Long.MAX_VALUE,
                    "double" to 3.14,
                ),
                null,
            )

        assertSoftly {
            args.string("str", "def") shouldBe "v"
            args.boolean("bool", false) shouldBe true
            args.int("int", 0) shouldBe 42
            args.long("long", 0L) shouldBe Long.MAX_VALUE
            args.double("double", 0.0) shouldBe 3.14
        }
    }

    @Test
    fun `accessors with default return the default when the key is missing or JSON null`() {
        val args = Args.of(null, null)
        val nullArgs = Args.of(mapOf<String, Any?>("str" to null, "long" to null), null)

        assertSoftly {
            args.string("k", "def") shouldBe "def"
            args.boolean("k", true) shouldBe true
            args.int("k", 7) shouldBe 7
            args.long("k", 42L) shouldBe 42L
            args.double("k", 1.5) shouldBe 1.5
            nullArgs.string("str", "def") shouldBe "def"
            nullArgs.long("long", 42L) shouldBe 42L
        }
    }

    @Serializable
    data class GreetingArgs(
        val name: String,
        val age: Int = 0,
    )

    // region: decode — typed via configured serde

    @Test
    fun `decode round-trip via configured serde`() {
        val raw =
            mapOf(
                "name" to "Alice",
                "age" to 30,
            )
        val args = Args.of(raw, KxSerializationSerde.Default)
        val decoded = args.decode(GreetingArgs::class.java)
        decoded shouldBe GreetingArgs("Alice", 30)
    }

    @Test
    fun `reified decode round-trip via configured serde`() {
        val args = Args.of(mapOf("name" to "Alice", "age" to 30), KxSerializationSerde.Default)

        args.decode<GreetingArgs>() shouldBe GreetingArgs("Alice", 30)
    }

    @Test
    fun `decode uses default values`() {
        val raw = mapOf("name" to "Bob")
        val args = Args.of(raw, KxSerializationSerde.Default)
        val decoded = args.decode(GreetingArgs::class.java)
        decoded shouldBe GreetingArgs("Bob", 0)
    }

    @Test
    fun `decode ignores unknown keys with default serde`() {
        val raw =
            mapOf(
                "name" to "Eve",
                "unexpected" to "extra",
            )
        val args = Args.of(raw, KxSerializationSerde.Default)
        val decoded = args.decode(GreetingArgs::class.java)
        decoded shouldBe GreetingArgs("Eve", 0)
    }

    @Test
    fun `decode uses configured serde not hardcoded json`() {
        val raw =
            mapOf(
                "name" to "Eve",
                "age" to 25,
                "unknown" to "extra",
            )
        // Default serde ignores unknown keys; a strict serde rejects them — proving the
        // configured deserializer is used, mapped to InvalidArgumentException (invalid params).
        val strictSerde = KxSerializationSerde(Json { ignoreUnknownKeys = false })
        val args = Args.of(raw, strictSerde)
        shouldThrow<InvalidArgumentException> {
            args.decode(GreetingArgs::class.java)
        }.argName() shouldBe "arguments"
    }

    @Test
    fun `decode throws when no deserializer configured`() {
        val raw = mapOf("name" to "Bob")
        val args = Args.of(raw, null)
        shouldThrow<IllegalStateException> {
            args.decode(GreetingArgs::class.java)
        }.message shouldContain "PayloadDeserializer is not configured"
    }

    // endregion

    // region: success — typed result via configured serde

    @Test
    fun `success returns ToolResult with raw value`() {
        val value = GreetingArgs("Charlie", 25)
        withStatelessContext { ctx ->
            val args = Args.of(null, null)
            val request =
                ToolRequest
                    .builder()
                    .name("greet")
                    .arguments(args)
                    .build()
            val scope = ToolScope(ctx, args = args, request = request)
            val result = scope.success(value)
            result.shouldBeInstanceOf<ToolResult.Success>()
            result.structured().get() shouldBe value
        }
    }

    @Test
    fun `success with text sets content text`() {
        val value = GreetingArgs("Dave", 50)
        withStatelessContext { ctx ->
            val args = Args.of(null, null)
            val request =
                ToolRequest
                    .builder()
                    .name("greet")
                    .arguments(args)
                    .build()
            val scope = ToolScope(ctx, args = args, request = request)
            val result = scope.success(value, "custom text")
            result.shouldBeInstanceOf<ToolResult.Success>()
            result.structured().get() shouldBe value
            (result.content().first() as TextContent).text() shouldBe "custom text"
        }
    }

    // endregion
}
