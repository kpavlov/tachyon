// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.domain.Args
import dev.tachyonmcp.server.domain.InvalidArgumentException
import dev.tachyonmcp.server.domain.TextContent
import dev.tachyonmcp.server.features.tools.ToolRequest
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.kotlin.TachyonServer
import dev.tachyonmcp.server.kotlin.config.ToolScope
import dev.tachyonmcp.server.kotlin.config.success
import dev.tachyonmcp.server.kotlin.domain.boolean
import dev.tachyonmcp.server.kotlin.domain.booleanOrNull
import dev.tachyonmcp.server.kotlin.domain.decode
import dev.tachyonmcp.server.kotlin.domain.double
import dev.tachyonmcp.server.kotlin.domain.doubleOrNull
import dev.tachyonmcp.server.kotlin.domain.int
import dev.tachyonmcp.server.kotlin.domain.intOrNull
import dev.tachyonmcp.server.kotlin.json.KxSerializationSerde
import dev.tachyonmcp.server.kotlin.json.toJsonNode
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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory

internal class KotlinApiTest {
    // region: Overload resolution — all shapes compile
    // These tests verify overloads compile for all schema types

    @Test
    fun `JsonNode overload registers a tool with input and output schema`() {
        val schema: JsonNode = JsonNodeFactory.instance.objectNode().put("type", "object")
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
                    "str" to JsonNodeFactory.instance.stringNode("v"),
                    "int" to JsonNodeFactory.instance.numberNode(42),
                    "bool" to JsonNodeFactory.instance.booleanNode(true),
                    "double" to JsonNodeFactory.instance.numberNode(3.14),
                ),
                null,
            )

        assertSoftly {
            args.stringOrNull("str") shouldBe "v"
            args.intOrNull("int") shouldBe 42
            args.booleanOrNull("bool") shouldBe true
            args.doubleOrNull("double") shouldBe 3.14
        }
    }

    @Test
    fun `orNull accessors return null when the key is missing`() {
        val args = Args.of(null, null)

        assertSoftly {
            args.stringOrNull("k") shouldBe null
            args.intOrNull("k") shouldBe null
            args.booleanOrNull("k") shouldBe null
            args.doubleOrNull("k") shouldBe null
        }
    }

    // endregion

    // region: Args boolean/int/double with defaults

    @Test
    fun `accessors with default return the value when the key is present`() {
        val args =
            Args.of(
                mapOf(
                    "bool" to JsonNodeFactory.instance.booleanNode(true),
                    "int" to JsonNodeFactory.instance.numberNode(42),
                    "double" to JsonNodeFactory.instance.numberNode(3.14),
                ),
                null,
            )

        assertSoftly {
            args.boolean("bool", false) shouldBe true
            args.int("int", 0) shouldBe 42
            args.double("double", 0.0) shouldBe 3.14
        }
    }

    @Test
    fun `accessors with default return the default when the key is missing`() {
        val args = Args.of(null, null)

        assertSoftly {
            args.boolean("k", true) shouldBe true
            args.int("k", 7) shouldBe 7
            args.double("k", 1.5) shouldBe 1.5
        }
    }

    // endregion

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
                "name" to JsonNodeFactory.instance.stringNode("Alice"),
                "age" to JsonNodeFactory.instance.numberNode(30),
            )
        val args = Args.of(raw, KxSerializationSerde.Default)
        val decoded = args.decode<GreetingArgs>()
        decoded shouldBe GreetingArgs("Alice", 30)
    }

    @Test
    fun `decode uses default values`() {
        val raw = mapOf("name" to JsonNodeFactory.instance.stringNode("Bob"))
        val args = Args.of(raw, KxSerializationSerde.Default)
        val decoded = args.decode<GreetingArgs>()
        decoded shouldBe GreetingArgs("Bob", 0)
    }

    @Test
    fun `decode ignores unknown keys with default serde`() {
        val raw =
            mapOf(
                "name" to JsonNodeFactory.instance.stringNode("Eve"),
                "unexpected" to JsonNodeFactory.instance.stringNode("extra"),
            )
        val args = Args.of(raw, KxSerializationSerde.Default)
        val decoded = args.decode<GreetingArgs>()
        decoded shouldBe GreetingArgs("Eve", 0)
    }

    @Test
    fun `decode uses configured serde not hardcoded json`() {
        val raw =
            mapOf(
                "name" to JsonNodeFactory.instance.stringNode("Eve"),
                "age" to JsonNodeFactory.instance.numberNode(25),
                "unknown" to JsonNodeFactory.instance.stringNode("extra"),
            )
        // Default serde ignores unknown keys; a strict serde rejects them — proving the
        // configured deserializer is used, mapped to InvalidArgumentException (invalid params).
        val strictSerde = KxSerializationSerde(Json { ignoreUnknownKeys = false })
        val args = Args.of(raw, strictSerde)
        shouldThrow<InvalidArgumentException> {
            args.decode<GreetingArgs>()
        }.argName() shouldBe "arguments"
    }

    @Test
    fun `decode throws when no deserializer configured`() {
        val raw = mapOf("name" to JsonNodeFactory.instance.stringNode("Bob"))
        val args = Args.of(raw, null)
        shouldThrow<IllegalStateException> {
            args.decode<GreetingArgs>()
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
