// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.config.ToolScope
import dev.tachyonmcp.server.config.success
import dev.tachyonmcp.server.domain.TextContent
import dev.tachyonmcp.server.domain.boolean
import dev.tachyonmcp.server.domain.booleanOrNull
import dev.tachyonmcp.server.domain.decode
import dev.tachyonmcp.server.domain.double
import dev.tachyonmcp.server.domain.doubleOrNull
import dev.tachyonmcp.server.domain.int
import dev.tachyonmcp.server.domain.intOrNull
import dev.tachyonmcp.server.domain.stringOrNull
import dev.tachyonmcp.server.features.tools.Args
import dev.tachyonmcp.server.features.tools.InvalidArgumentException
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.internal.ServerEngine
import dev.tachyonmcp.server.json.KxSerializationSerde
import dev.tachyonmcp.server.json.toJsonNode
import dev.tachyonmcp.server.session.DefaultDispatchContext
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
    fun `JsonNode overload compiles and registers tool`() {
        val schema: JsonNode = JsonNodeFactory.instance.objectNode().put("type", "object")
        TachyonServer(port = 0) {
            name("test")
            tool("t1", inputSchema = schema) { ToolResult.text("ok") }
        }.use { handle ->
            handle.tools().getDescriptor("t1") shouldNotBe null
        }
    }

    @Test
    fun `JsonNode overload with outputSchema compiles`() {
        val schema: JsonNode = JsonNodeFactory.instance.objectNode().put("type", "object")
        TachyonServer(port = 0) {
            name("test")
            tool("t2", inputSchema = schema, outputSchema = schema) { ToolResult.text("ok") }
        }.use { handle ->
            handle.tools().getDescriptor("t2") shouldNotBe null
        }
    }

    @Test
    fun `String overload compiles and registers tool`() {
        val json = """{"type":"object"}"""
        TachyonServer(port = 0) {
            name("test")
            tool("t3", inputSchema = json) { ToolResult.text("ok") }
        }.use { handle ->
            handle.tools().getDescriptor("t3") shouldNotBe null
        }
    }

    @Test
    fun `String overload with outputSchema compiles`() {
        val json = """{"type":"object"}"""
        TachyonServer(port = 0) {
            name("test")
            tool("t4", inputSchema = json, outputSchema = json) { ToolResult.text("ok") }
        }.use { handle ->
            handle.tools().getDescriptor("t4") shouldNotBe null
        }
    }

    @Test
    fun `JsonObject overload compiles and registers tool`() {
        val schema = buildJsonObject { put("type", "object") }
        TachyonServer(port = 0) {
            name("test")
            tool("t5", inputSchema = schema) { ToolResult.text("ok") }
        }.use { handle ->
            handle.tools().getDescriptor("t5") shouldNotBe null
        }
    }

    @Test
    fun `JsonObject overload with outputSchema compiles`() {
        val schema = buildJsonObject { put("type", "object") }
        TachyonServer(port = 0) {
            name("test")
            tool("t6", inputSchema = schema, outputSchema = schema) { ToolResult.text("ok") }
        }.use { handle ->
            handle.tools().getDescriptor("t6") shouldNotBe null
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
    fun `stringOrNull returns value when present`() {
        val args = Args(raw = mapOf("k" to JsonNodeFactory.instance.stringNode("v")))
        args.stringOrNull("k") shouldBe "v"
    }

    @Test
    fun `stringOrNull returns null when missing`() {
        val args = Args()
        args.stringOrNull("k") shouldBe null
    }

    @Test
    fun `intOrNull returns value when present`() {
        val args = Args(raw = mapOf("k" to JsonNodeFactory.instance.numberNode(42)))
        args.intOrNull("k") shouldBe 42
    }

    @Test
    fun `intOrNull returns null when missing`() {
        val args = Args()
        args.intOrNull("k") shouldBe null
    }

    @Test
    fun `booleanOrNull returns value when present`() {
        val args = Args(raw = mapOf("k" to JsonNodeFactory.instance.booleanNode(true)))
        args.booleanOrNull("k") shouldBe true
    }

    @Test
    fun `booleanOrNull returns null when missing`() {
        val args = Args()
        args.booleanOrNull("k") shouldBe null
    }

    @Test
    fun `doubleOrNull returns value when present`() {
        val args = Args(raw = mapOf("k" to JsonNodeFactory.instance.numberNode(3.14)))
        args.doubleOrNull("k") shouldBe 3.14
    }

    @Test
    fun `doubleOrNull returns null when missing`() {
        val args = Args()
        args.doubleOrNull("k") shouldBe null
    }

    // endregion

    // region: Args boolean/int/double with defaults

    @Test
    fun `boolean with default returns value`() {
        val args = Args(raw = mapOf("k" to JsonNodeFactory.instance.booleanNode(true)))
        args.boolean("k", false) shouldBe true
    }

    @Test
    fun `boolean with default returns default`() {
        val args = Args()
        args.boolean("k", true) shouldBe true
    }

    @Test
    fun `int with default returns value`() {
        val args = Args(raw = mapOf("k" to JsonNodeFactory.instance.numberNode(42)))
        args.int("k", 0) shouldBe 42
    }

    @Test
    fun `double with default returns value`() {
        val args = Args(raw = mapOf("k" to JsonNodeFactory.instance.numberNode(3.14)))
        args.double("k", 0.0) shouldBe 3.14
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
        val args = Args(raw = raw, deserializer = KxSerializationSerde.Default)
        val decoded = args.decode<GreetingArgs>()
        decoded shouldBe GreetingArgs("Alice", 30)
    }

    @Test
    fun `decode uses default values`() {
        val raw = mapOf("name" to JsonNodeFactory.instance.stringNode("Bob"))
        val args = Args(raw = raw, deserializer = KxSerializationSerde.Default)
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
        val args = Args(raw = raw, deserializer = KxSerializationSerde.Default)
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
        val args = Args(raw = raw, deserializer = strictSerde)
        shouldThrow<InvalidArgumentException> {
            args.decode<GreetingArgs>()
        }.argName() shouldBe "arguments"
    }

    @Test
    fun `decode throws when no deserializer configured`() {
        val raw = mapOf("name" to JsonNodeFactory.instance.stringNode("Bob"))
        val args = Args(raw = raw)
        shouldThrow<IllegalStateException> {
            args.decode<GreetingArgs>()
        }.message shouldContain "PayloadDeserializer is not configured"
    }

    // endregion

    // region: success — typed result via configured serde

    @Test
    fun `success returns ToolResult with raw value`() {
        val value = GreetingArgs("Charlie", 25)
        TachyonServer.builder().build().use { server ->
            val ctx = DefaultDispatchContext.stateless(server as ServerEngine)
            val scope = ToolScope(ctx, Args(raw = null))
            val result = scope.success(value)
            result.shouldBeInstanceOf<ToolResult.Success>()
            result.structured().get() shouldBe value
        }
    }

    @Test
    fun `success with text sets content text`() {
        val value = GreetingArgs("Dave", 50)
        TachyonServer.builder().build().use { server ->
            val ctx = DefaultDispatchContext.stateless(server as ServerEngine)
            val scope = ToolScope(ctx, Args(raw = null))
            val result = scope.success(value, "custom text")
            result.shouldBeInstanceOf<ToolResult.Success>()
            result.structured().get() shouldBe value
            (result.content().first() as TextContent).text() shouldBe "custom text"
        }
    }

    // endregion
}
