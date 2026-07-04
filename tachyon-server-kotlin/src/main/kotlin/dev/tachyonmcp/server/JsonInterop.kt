// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.features.tools.DefaultToolDescriptor
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

@PublishedApi
internal val sharedMapper: ObjectMapper = JsonMapper.builder().build()

@PublishedApi
@Suppress("TooGenericExceptionCaught")
internal fun String.toJsonNode(): JsonNode =
    try {
        sharedMapper.readTree(this)
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to parse JSON schema: '$this'", e)
    }

@PublishedApi
internal fun DefaultToolDescriptor.Builder.schemas(
    inputSchema: String,
    outputSchema: String?,
): DefaultToolDescriptor.Builder =
    inputSchema(inputSchema.toJsonNode()).outputSchema(outputSchema?.toJsonNode())
