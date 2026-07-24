// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.json

import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.json.JsonSchemaUtils.parseSchema
import dev.tachyonmcp.server.json.JsonUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import java.math.BigDecimal

private val nodes = JsonNodeFactory.instance

internal fun String.toJsonNode(): JsonNode =
    try {
        JsonUtils.parse(this)
    } catch (e: JacksonException) {
        throw IllegalArgumentException("Failed to parse JSON schema: '$this'", e)
    }

internal fun ToolDescriptor.Builder.schemas(
    inputSchema: String? = null,
    outputSchema: String? = null,
): ToolDescriptor.Builder {
    if (inputSchema != null) {
        inputSchema(parseSchema(inputSchema))
    }
    if (outputSchema != null) {
        outputSchema(parseSchema(outputSchema))
    }
    return this
}

internal fun JsonElement.toJacksonNode(): JsonNode =
    when (this) {
        is JsonNull -> {
            nodes.nullNode()
        }

        is JsonPrimitive -> {
            toValueNode()
        }

        is JsonArray -> {
            nodes.arrayNode(size).also { array ->
                forEach { array.add(it.toJacksonNode()) }
            }
        }

        is JsonObject -> {
            nodes.objectNode().also { obj ->
                forEach { (key, value) -> obj.set(key, value.toJacksonNode()) }
            }
        }
    }

// node types must mirror what JsonUtils.parse produces, so both routes compare equal
private fun JsonPrimitive.toValueNode(): JsonNode =
    when {
        isString -> {
            nodes.stringNode(content)
        }

        else -> {
            booleanOrNull?.let(nodes::booleanNode)
                ?: intOrNull?.let(nodes::numberNode)
                ?: longOrNull?.let(nodes::numberNode)
                ?: content.toBigIntegerOrNull()?.let(nodes::numberNode)
                ?: doubleOrNull?.let(nodes::numberNode)
                ?: nodes.numberNode(BigDecimal(content))
        }
    }

internal fun JsonObject?.toJacksonNodeOrNull(): JsonNode? = this?.toJacksonNode()

internal fun Map<String, JsonObject>.toJacksonNodeMap(): Map<String, JsonNode> =
    mapValues { (_, v) -> v.toJacksonNode() }
