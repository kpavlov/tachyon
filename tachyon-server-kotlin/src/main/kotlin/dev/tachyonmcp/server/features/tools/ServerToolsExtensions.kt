/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools

import dev.tachyonmcp.server.Server
import dev.tachyonmcp.server.config.ToolScope
import dev.tachyonmcp.server.json.schemas
import dev.tachyonmcp.server.json.toJacksonNode
import dev.tachyonmcp.server.json.toJacksonNodeOrNull
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@JvmSynthetic
public fun Server.registerTool(
    name: String,
    description: String? = null,
    inputSchema: JsonNode? = null,
    outputSchema: JsonNode? = null,
    block: suspend ToolScope.() -> ToolResult,
): Server =
    registerTool(configure = {
        name(name)
        description(description)
        inputSchema(inputSchema)
        outputSchema(outputSchema)
    }, block = block)

@JvmSynthetic
public fun Server.registerTool(
    name: String,
    description: String? = null,
    inputSchema: String,
    outputSchema: String? = null,
    block: suspend ToolScope.() -> ToolResult,
): Server =
    registerTool(configure = {
        name(name)
        description(description)
        schemas(inputSchema, outputSchema, toolName = name)
    }, block = block)

@JvmSynthetic
@OptIn(ExperimentalContracts::class)
public inline fun Server.registerTool(
    configure: ToolDescriptor.Builder.() -> Unit = {},
    noinline block: suspend ToolScope.() -> ToolResult,
): Server {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    return registerTool(
        descriptor = ToolDescriptor.builder().apply(configure).build(),
        block = block,
    )
}

@JvmSynthetic
public fun Server.registerTool(
    descriptor: ToolDescriptor,
    block: suspend ToolScope.() -> ToolResult,
): Server {
    this.registerTool(asyncHandler(descriptor, block))
    return this
}

/**
 * Registers a tool using a [JsonObject] input schema.
 * Requires kotlinx-serialization-json on the classpath.
 */
@JvmSynthetic
public fun Server.registerTool(
    name: String,
    description: String? = null,
    inputSchema: JsonObject,
    outputSchema: JsonObject? = null,
    block: suspend ToolScope.() -> ToolResult,
): Server =
    registerTool(
        name = name,
        description = description,
        inputSchema = inputSchema.toJacksonNode(),
        outputSchema = outputSchema.toJacksonNodeOrNull(),
        block = block,
    )
