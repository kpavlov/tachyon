// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.config.ToolScope
import dev.tachyonmcp.server.config.structured
import dev.tachyonmcp.server.config.success
import dev.tachyonmcp.server.domain.TextContent
import dev.tachyonmcp.server.features.tools.InvalidArgumentException
import dev.tachyonmcp.server.features.tools.ToolArgs
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.features.tools.boolean
import dev.tachyonmcp.server.features.tools.booleanOrNull
import dev.tachyonmcp.server.features.tools.decode
import dev.tachyonmcp.server.features.tools.double
import dev.tachyonmcp.server.features.tools.doubleOrNull
import dev.tachyonmcp.server.features.tools.int
import dev.tachyonmcp.server.features.tools.intOrNull
import dev.tachyonmcp.server.features.tools.stringOrNull
import dev.tachyonmcp.server.json.KxSerializationSerde
import dev.tachyonmcp.server.json.RawJson
import dev.tachyonmcp.server.json.toJsonNode
import dev.tachyonmcp.server.session.DefaultMcpContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
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
            handle.server().getTool("t1") shouldNotBe null
        }
    }

    @Test
    fun `JsonNode overload with outputSchema compiles`() {
        val schema: JsonNode = JsonNodeFactory.instance.objectNode().put("type", "object")
        TachyonServer(port = 0) {
            name("test")
            tool("t2", inputSchema = schema, outputSchema = schema) { ToolResult.text("ok") }
        }.use { handle ->
            handle.server().getTool("t2") shouldNotBe null
        }
    }

    @Test
    fun `String overload compiles and registers tool`() {
        val json = """{"type":"object"}"""
        TachyonServer(port = 0) {
            name("test")
            tool("t3", inputSchema = json) { ToolResult.text("ok") }
        }.use { handle ->
            handle.server().getTool("t3") shouldNotBe null
        }
    }

    @Test
    fun `String overload with outputSchema compiles`() {
        val json = """{"type":"object"}"""
        TachyonServer(port = 0) {
            name("test")
            tool("t4", inputSchema = json, outputSchema = json) { ToolResult.text("ok") }
        }.use { handle ->
            handle.server().getTool("t4") shouldNotBe null
        }
    }

    @Test
    fun `JsonObject overload compiles and registers tool`() {
        val schema = buildJsonObject { put("type", "object") }
        TachyonServer(port = 0) {
            name("test")
            tool("t5", inputSchema = schema) { ToolResult.text("ok") }
        }.use { handle ->
            handle.server().getTool("t5") shouldNotBe null
        }
    }

    @Test
    fun `JsonObject overload with outputSchema compiles`() {
        val schema = buildJsonObject { put("type", "object") }
        TachyonServer(port = 0) {
            name("test")
            tool("t6", inputSchema = schema, outputSchema = schema) { ToolResult.text("ok") }
        }.use { handle ->
            handle.server().getTool("t6") shouldNotBe null
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

    // region: ToolArgs orNull sugar

    @Test
    fun `stringOrNull returns value when present`() {
        val args = ToolArgs(raw = mapOf("k" to JsonNodeFactory.instance.stringNode("v")))
        args.stringOrNull("k") shouldBe "v"
    }

    @Test
    fun `stringOrNull returns null when missing`() {
        val args = ToolArgs()
        args.stringOrNull("k") shouldBe null
    }

    @Test
    fun `intOrNull returns value when present`() {
        val args = ToolArgs(raw = mapOf("k" to JsonNodeFactory.instance.numberNode(42)))
        args.intOrNull("k") shouldBe 42
    }

    @Test
    fun `intOrNull returns null when missing`() {
        val args = ToolArgs()
        args.intOrNull("k") shouldBe null
    }

    @Test
    fun `booleanOrNull returns value when present`() {
        val args = ToolArgs(raw = mapOf("k" to JsonNodeFactory.instance.booleanNode(true)))
        args.booleanOrNull("k") shouldBe true
    }

    @Test
    fun `booleanOrNull returns null when missing`() {
        val args = ToolArgs()
        args.booleanOrNull("k") shouldBe null
    }

    @Test
    fun `doubleOrNull returns value when present`() {
        val args = ToolArgs(raw = mapOf("k" to JsonNodeFactory.instance.numberNode(3.14)))
        args.doubleOrNull("k") shouldBe 3.14
    }

    @Test
    fun `doubleOrNull returns null when missing`() {
        val args = ToolArgs()
        args.doubleOrNull("k") shouldBe null
    }

    // endregion

    // region: ToolArgs boolean/int/double with defaults

    @Test
    fun `boolean with default returns value`() {
        val args = ToolArgs(raw = mapOf("k" to JsonNodeFactory.instance.booleanNode(true)))
        args.boolean("k", false) shouldBe true
    }

    @Test
    fun `boolean with default returns default`() {
        val args = ToolArgs()
        args.boolean("k", true) shouldBe true
    }

    @Test
    fun `int with default returns value`() {
        val args = ToolArgs(raw = mapOf("k" to JsonNodeFactory.instance.numberNode(42)))
        args.int("k", 0) shouldBe 42
    }

    @Test
    fun `double with default returns value`() {
        val args = ToolArgs(raw = mapOf("k" to JsonNodeFactory.instance.numberNode(3.14)))
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
        val args = ToolArgs(raw = raw, deserializer = KxSerializationSerde.Default)
        val decoded = args.decode<GreetingArgs>()
        decoded shouldBe GreetingArgs("Alice", 30)
    }

    @Test
    fun `decode uses default values`() {
        val raw = mapOf("name" to JsonNodeFactory.instance.stringNode("Bob"))
        val args = ToolArgs(raw = raw, deserializer = KxSerializationSerde.Default)
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
        val args = ToolArgs(raw = raw, deserializer = KxSerializationSerde.Default)
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
        val args = ToolArgs(raw = raw, deserializer = strictSerde)
        shouldThrow<InvalidArgumentException> {
            args.decode<GreetingArgs>()
        }.argName() shouldBe "arguments"
    }

    @Test
    fun `decode throws when no deserializer configured`() {
        val raw = mapOf("name" to JsonNodeFactory.instance.stringNode("Bob"))
        val args = ToolArgs(raw = raw)
        shouldThrow<IllegalStateException> {
            args.decode<GreetingArgs>()
        }.message shouldContain "PayloadDeserializer is not configured"
    }

    // endregion

    // region: structured

    @Test
    fun `structured produces ToolResult with raw json and text fallback`() {
        val value = GreetingArgs("Charlie", 25)
        TachyonServer.builder().build().use { server ->
            val ctx = DefaultMcpContext.stateless(server)
            val scope = ToolScope(ctx, ToolArgs(raw = null))
            val result = scope.structured(value)
            result.shouldBeInstanceOf<ToolResult.Success>()
            result.structured() shouldNotBeNull {
                get().shouldBeInstanceOf<RawJson>()
            }
            (result.content().first() as TextContent).text() shouldBe
                """{"name":"Charlie","age":25}"""
        }
    }

    @Test
    fun `structured uses custom Json instance`() {
        val value = GreetingArgs("Dave", 50)
        val customJson = Json { prettyPrint = true }
        TachyonServer.builder().build().use { server ->
            val ctx = DefaultMcpContext.stateless(server)
            val scope = ToolScope(ctx, ToolArgs(raw = null))
            val result = scope.structured(value, customJson)
            result.shouldBeInstanceOf<ToolResult.Success>()
            result.structured() shouldNotBeNull {
                get().shouldBeInstanceOf<RawJson>()
            }
            (result.content().first() as TextContent).text() shouldContain "\"name\""
        }
    }

    @Test
    fun `structured rejects non-object payloads`() {
        TachyonServer.builder().build().use { server ->
            val ctx = DefaultMcpContext.stateless(server)
            val scope = ToolScope(ctx, ToolArgs(raw = null))
            shouldThrow<IllegalArgumentException> {
                scope.structured(listOf(1, 2, 3))
            }.message shouldContain "must be a JSON object"
        }
    }

    // endregion

    // region: success — typed result via configured serde

    @Test
    fun `success returns ToolResult with raw value`() {
        val value = GreetingArgs("Charlie", 25)
        TachyonServer.builder().build().use { server ->
            val ctx = DefaultMcpContext.stateless(server)
            val scope = ToolScope(ctx, ToolArgs(raw = null))
            val result = scope.success(value)
            result.shouldBeInstanceOf<ToolResult.Success>()
            result.structured().get() shouldBe value
        }
    }

    @Test
    fun `success with text sets content text`() {
        val value = GreetingArgs("Dave", 50)
        TachyonServer.builder().build().use { server ->
            val ctx = DefaultMcpContext.stateless(server)
            val scope = ToolScope(ctx, ToolArgs(raw = null))
            val result = scope.success(value, "custom text")
            result.shouldBeInstanceOf<ToolResult.Success>()
            result.structured().get() shouldBe value
            (result.content().first() as TextContent).text() shouldBe "custom text"
        }
    }

    // endregion
}
