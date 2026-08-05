// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server

import dev.tachyonmcp.api.server.domain.Args
import dev.tachyonmcp.api.server.domain.AudioContent
import dev.tachyonmcp.api.server.domain.ImageContent
import dev.tachyonmcp.api.server.domain.InvalidArgumentException
import dev.tachyonmcp.api.server.domain.Role
import dev.tachyonmcp.api.server.domain.TextContent
import dev.tachyonmcp.api.server.features.prompts.PromptRequest
import dev.tachyonmcp.api.server.features.tasks.TaskSupport
import dev.tachyonmcp.api.server.features.tools.ToolRequest
import dev.tachyonmcp.api.server.features.tools.ToolResult
import dev.tachyonmcp.kotlin.server.config.PromptScope
import dev.tachyonmcp.kotlin.server.config.ToolScope
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
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.stream.Stream
import dev.tachyonmcp.api.json.JsonSchema as JavaJsonSchema

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
                tool("t1", inputSchema = schema, outputSchema = schema) {
                    ToolResult.text("ok")
                }
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
                tool("t2", inputSchema = json, outputSchema = json) {
                    ToolResult.text("ok")
                }
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
                tool("t3", inputSchema = schema, outputSchema = schema) {
                    ToolResult.text("ok")
                }
            },
        ).use { handle ->
            handle.tools().find("t3").orElse(null) shouldNotBe null
        }
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

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("missingOrJsonNullArguments")
    fun `orNull and default accessors and decode fold missing and JSON-null arguments the same way`(
        @Suppress("UnusedParameter") scenario: String,
        argumentsJson: String,
    ) {
        TachyonServer(port = 0) {
            name("null-accessors-test")
            session { enabled = true }
            tool("null-accessors") {
                val args = request.arguments()
                val fields =
                    listOf(
                        "stringOrNull" to args.stringOrNull("str"),
                        "intOrNull" to args.intOrNull("int"),
                        "longOrNull" to args.longOrNull("long"),
                        "booleanOrNull" to args.booleanOrNull("bool"),
                        "doubleOrNull" to args.doubleOrNull("double"),
                        "decimalOrNull" to args.decimalOrNull("decimal"),
                        "objectOrNull" to args.objectOrNull("obj"),
                        "arrayOrNull" to args.arrayOrNull("arr"),
                        "stringDefault" to args.string("str", "default"),
                        "booleanDefault" to args.boolean("bool", true),
                        "intDefault" to args.int("int", 7),
                        "longDefault" to args.long("long", 42L),
                        "doubleDefault" to args.double("double", 1.5),
                        "decodeStr" to args.decode<NullableArgs>().str,
                    )
                ToolResult.raw(
                    ObjectMapper().writeValueAsString(fields.toMap()),
                    "accessors resolved",
                )
            }
        }.use { server ->
            McpProbe(server.port()).use { probe ->
                probe.initialize()
                val response = probe.callTool("null-accessors", argumentsJson)
                response.statusCode() shouldBe 200
                response.body() shouldEqualJson
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "result": {
                        "content": [{"type": "text", "text": "accessors resolved"}],
                        "structuredContent": {
                          "stringOrNull": null,
                          "intOrNull": null,
                          "longOrNull": null,
                          "booleanOrNull": null,
                          "doubleOrNull": null,
                          "decimalOrNull": null,
                          "objectOrNull": null,
                          "arrayOrNull": null,
                          "stringDefault": "default",
                          "booleanDefault": true,
                          "intDefault": 7,
                          "longDefault": 42,
                          "doubleDefault": 1.5,
                          "decodeStr": null
                        }
                      }
                    }
                    """.trimIndent()
            }
        }
    }

    // endregion

    companion object {
        @JvmStatic
        fun missingOrJsonNullArguments(): Stream<Arguments> =
            Stream.of(
                Arguments.of("missing arguments", "{}"),
                Arguments.of(
                    "JSON-null arguments",
                    // language=json
                    """
                    {"str":null,"int":null,"long":null,"bool":null,"double":null,
                     "decimal":null,"obj":null,"arr":null}
                    """.trimIndent(),
                ),
            )
    }

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

    @Serializable
    data class NullableArgs(
        val str: String? = null,
    )

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
            val scope = ToolScope(ctx, request = request)
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
            val scope = ToolScope(ctx, request = request)
            val result = scope.success(value, "custom text")
            result.shouldBeInstanceOf<ToolResult.Success>()
            result.structured().get() shouldBe value
            (result.content().first() as TextContent).text() shouldBe "custom text"
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `content DSL collects text and image blocks into a ToolResult`() {
        withStatelessContext { ctx ->
            val request =
                ToolRequest
                    .builder()
                    .name("render")
                    .arguments(Args.of(null, null))
                    .build()
            val scope = ToolScope(ctx, request = request)
            val result =
                scope.content {
                    text("Answer")
                    image("aGVsbG8=", "image/png")
                }
            result.shouldBeInstanceOf<ToolResult.Success>()
            assertSoftly {
                result.content() shouldHaveSize 2
                (result.content()[0] as TextContent).text() shouldBe "Answer"
                (result.content()[1] as ImageContent).mimeType() shouldBe "image/png"
            }
        }
    }

    @Test
    fun `content DSL accepts raw byte array image and audio blocks`() {
        withStatelessContext { ctx ->
            val request =
                ToolRequest
                    .builder()
                    .name("render")
                    .arguments(Args.of(null, null))
                    .build()
            val scope = ToolScope(ctx, request = request)
            val result =
                scope.content {
                    image(byteArrayOf(1, 2, 3), "image/png")
                    audio(byteArrayOf(4, 5, 6), "audio/wav")
                }
            result.shouldBeInstanceOf<ToolResult.Success>()
            assertSoftly {
                result.content() shouldHaveSize 2
                (result.content()[0] as ImageContent).data().toList() shouldBe listOf<Byte>(1, 2, 3)
                (result.content()[1] as AudioContent).data().toList() shouldBe listOf<Byte>(4, 5, 6)
            }
        }
    }

    @Test
    fun `text DSL returns a single-block ToolResult`() {
        withStatelessContext { ctx ->
            val request =
                ToolRequest
                    .builder()
                    .name("say")
                    .arguments(Args.of(null, null))
                    .build()
            val scope = ToolScope(ctx, request = request)
            val result = scope.text("hi")
            result.shouldBeInstanceOf<ToolResult.Success>()
            (result.content().single() as TextContent).text() shouldBe "hi"
        }
    }

    // endregion

    // region: ToolScope convenience accessors

    @Test
    fun `ToolScope arguments delegates to request arguments`() {
        withStatelessContext { ctx ->
            val args = Args.of(mapOf("k" to "v"), null)
            val request =
                ToolRequest
                    .builder()
                    .name("t")
                    .arguments(args)
                    .build()
            val scope = ToolScope(ctx, request = request)
            scope.arguments shouldBe args
        }
    }

    @Test
    fun `ToolScope task delegates to request task`() {
        withStatelessContext { ctx ->
            val request =
                ToolRequest
                    .builder()
                    .name("t")
                    .arguments(Args.of(null, null))
                    .build()
            val scope = ToolScope(ctx, request = request)
            scope.task shouldBe null
        }
    }

    @Test
    fun `ToolScope fail returns a failed ToolResult with the message`() {
        withStatelessContext { ctx ->
            val request =
                ToolRequest
                    .builder()
                    .name("t")
                    .arguments(Args.of(null, null))
                    .build()
            val scope = ToolScope(ctx, request = request)
            val result = scope.fail("boom")
            result.shouldBeInstanceOf<ToolResult.Error>()
            (result.content().single() as TextContent).text() shouldBe "boom"
        }
    }

    @Test
    fun `tool builder sets taskSupport on the registered descriptor`() {
        TachyonServer(port = 0) {
            name("task-support-test")
            tool("t-task", taskSupport = TaskSupport.OPTIONAL) {
                ToolResult.text("ok")
            }
        }.use { handle ->
            handle
                .tools()
                .find("t-task")
                .orElse(null)
                ?.taskSupport() shouldBe TaskSupport.OPTIONAL
        }
    }

    // endregion

    @Test
    @Suppress("DEPRECATION")
    fun `PromptScope content DSL builds one user message per block`() {
        withStatelessContext { ctx ->
            val request = PromptRequest(Args.empty(), null, null)
            val scope = PromptScope(ctx, request = request)
            val messages =
                scope.content {
                    text("Summarize this")
                    image("aGVsbG8=", "image/png")
                }
            assertSoftly {
                messages shouldHaveSize 2
                messages.forEach { it.role() shouldBe Role.USER }
                (messages[0].content() as TextContent).text() shouldBe "Summarize this"
                (messages[1].content() as ImageContent).mimeType() shouldBe "image/png"
            }
        }
    }

    // endregion
}
