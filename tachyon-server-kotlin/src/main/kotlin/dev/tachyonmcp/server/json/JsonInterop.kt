// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.json

import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.json.JsonSchemaUtils.parseSchema
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode

@Suppress("TooGenericExceptionCaught")
internal fun String.toJsonNode(): JsonNode =
    try {
        JsonUtils.parse(this)
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to parse JSON schema: '$this'", e)
    }

internal fun ToolDescriptor.Builder.schemas(
    inputSchema: String,
    outputSchema: String?,
    toolName: String,
): ToolDescriptor.Builder =
    inputSchema(parseSchema(inputSchema, toolName))
        .outputSchema(outputSchema?.let { parseSchema(it, toolName) })

internal fun JsonObject.toJacksonNode(): JsonNode = toString().toJsonNode()

internal fun JsonObject?.toJacksonNodeOrNull(): JsonNode? = this?.toJacksonNode()
