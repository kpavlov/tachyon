// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.ServerBuilder
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.features.tools.asyncHandler
import dev.tachyonmcp.server.json.schemas
import dev.tachyonmcp.server.json.toJacksonNode
import dev.tachyonmcp.server.json.toJacksonNodeOrNull
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode

public fun ServerBuilder.resource(
    name: String,
    uri: String,
    description: String? = null,
    mimeType: String = "application/json",
    handler: ResourceScope.() -> ResourceContents,
): ServerBuilder =
    resource(
        ResourceDescriptor(name = name, uri = uri, description = description, mimeType = mimeType),
    ) { ctx, req ->
        ResourceScope(ctx, req).handler()
    }

public fun ServerBuilder.tool(
    name: String,
    description: String? = null,
    inputSchema: JsonNode? = null,
    outputSchema: JsonNode? = null,
    handler: suspend ToolScope.() -> ToolResult,
): ServerBuilder =
    tool(
        asyncHandler(
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
        asyncHandler(
            ToolDescriptor
                .builder()
                .name(name)
                .description(description)
                .schemas(inputSchema, outputSchema, toolName = name)
                .build(),
            handler,
        ),
    )

/**
 * Registers a tool using a [JsonObject] input schema.
 * Requires kotlinx-serialization-json on the classpath.
 *
 * @author Konstantin Pavlov
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
