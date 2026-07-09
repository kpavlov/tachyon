// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.ServerBuilder
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.resources.resourceHandler
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.features.tools.toolHandler
import dev.tachyonmcp.server.json.schemas
import dev.tachyonmcp.server.json.toJacksonNode
import dev.tachyonmcp.server.json.toJacksonNodeOrNull
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode

public fun ServerBuilder.resource(
    name: String,
    uri: String,
    description: String? = null,
    mimeType: String? = null,
    handler: suspend ResourceScope.() -> ResourceContents,
): ServerBuilder {
    val descriptor =
        ResourceDescriptor(name = name, uri = uri, description = description, mimeType = mimeType)
    return resource(descriptor, resourceHandler(descriptor, handler))
}

public fun ServerBuilder.tool(
    name: String,
    description: String? = null,
    inputSchema: JsonNode? = null,
    outputSchema: JsonNode? = null,
    handler: suspend ToolScope.() -> ToolResult,
): ServerBuilder =
    tool(
        toolHandler(
            ToolDescriptor
                .builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .outputSchema(outputSchema)
                .build(),
            handler,
        ),
    )

public fun ServerBuilder.tool(
    name: String,
    description: String? = null,
    inputSchema: String,
    outputSchema: String? = null,
    handler: suspend ToolScope.() -> ToolResult,
): ServerBuilder =
    tool(
        toolHandler(
            ToolDescriptor
                .builder()
                .name(name)
                .description(description)
                .schemas(inputSchema, outputSchema)
                .build(),
            handler,
        ),
    )

/**
 * Registers a tool using a [JsonObject] input schema.
 * Requires kotlinx-serialization-json on the classpath.
 */
public fun ServerBuilder.tool(
    name: String,
    description: String? = null,
    inputSchema: JsonObject,
    outputSchema: JsonObject? = null,
    handler: suspend ToolScope.() -> ToolResult,
): ServerBuilder =
    tool(
        name = name,
        description = description,
        inputSchema = inputSchema.toJacksonNode(),
        outputSchema = outputSchema?.toJacksonNodeOrNull(),
        handler = handler,
    )
