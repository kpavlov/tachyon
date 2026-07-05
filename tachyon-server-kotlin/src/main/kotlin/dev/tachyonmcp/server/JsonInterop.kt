// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.json.JsonSchemaUtils.parseSchema
import dev.tachyonmcp.server.json.JsonUtils
import tools.jackson.databind.JsonNode

@PublishedApi
@Suppress("TooGenericExceptionCaught")
internal fun String.toJsonNode(): JsonNode {
//    if (this == null) return null
    return try {
        JsonUtils.parse(this)
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to parse JSON schema: '$this'", e)
    }
}

@PublishedApi
internal fun ToolDescriptor.Builder.schemas(
    inputSchema: String,
    outputSchema: String?,
    toolName: String,
): ToolDescriptor.Builder =
    inputSchema(parseSchema(inputSchema, toolName))
        .outputSchema(outputSchema?.let { parseSchema(it, toolName) })
