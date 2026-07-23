// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.ServerBuilder
import dev.tachyonmcp.server.domain.Annotations
import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.server.features.resources.resourceHandler
import dev.tachyonmcp.server.features.resources.templateHandler
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.features.tools.toolHandler
import dev.tachyonmcp.server.json.schemas
import dev.tachyonmcp.server.json.toJacksonNodeOrNull
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode

@JvmSynthetic
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

/**
 * Registers a resource template with a handler.
 *
 * @param uriTemplate The URI pattern used to identify matching resources.
 * @param handler The function that produces the resource contents.
 * @return This server builder.
 */
@JvmSynthetic
public fun ServerBuilder.resourceTemplate(
    name: String,
    uriTemplate: String,
    description: String? = null,
    mimeType: String? = null,
    title: String? = null,
    annotations: Annotations? = null,
    icons: List<Icon>? = null,
    block: suspend TemplateScope.() -> ResourceContents,
): ServerBuilder {
    val descriptor =
        ResourceTemplateDescriptor
            .builder()
            .name(name)
            .uriTemplate(uriTemplate)
            .description(description)
            .mimeType(mimeType)
            .title(title)
            .icons(icons)
            .annotations(annotations)
            .build()
    return resourceTemplate(descriptor, block)
}

/**
 * Registers a prebuilt resource-template descriptor with a suspending handler block.
 *
 * @param descriptor resource-template descriptor
 * @param block handler invoked for matching resource requests
 * @return this server builder
 */
@JvmSynthetic
public fun ServerBuilder.resourceTemplate(
    descriptor: ResourceTemplateDescriptor,
    block: suspend TemplateScope.() -> ResourceContents,
): ServerBuilder = resourceTemplate(descriptor, templateHandler(descriptor, block))

/**
 * Registers a tool with optional descriptions and JSON schemas.
 *
 * @param name The tool name.
 * @param description The tool description.
 * @param inputSchema The JSON schema for the tool input.
 * @param outputSchema The JSON schema for the tool output.
 * @param handler The function that handles tool invocations.
 * @return This server builder.
 */
@JvmSynthetic
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

@JvmSynthetic
public fun ServerBuilder.tool(
    name: String,
    description: String? = null,
    inputSchema: String? = null,
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
@JvmSynthetic
public fun ServerBuilder.tool(
    name: String,
    description: String? = null,
    inputSchema: JsonObject? = null,
    outputSchema: JsonObject? = null,
    handler: suspend ToolScope.() -> ToolResult,
): ServerBuilder =
    tool(
        name = name,
        description = description,
        inputSchema = inputSchema.toJacksonNodeOrNull(),
        outputSchema = outputSchema.toJacksonNodeOrNull(),
        handler = handler,
    )
