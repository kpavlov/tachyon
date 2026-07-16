/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tools

import dev.tachyonmcp.server.TachyonServer
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
public fun TachyonServer.registerTool(
    name: String,
    description: String? = null,
    inputSchema: JsonNode? = null,
    outputSchema: JsonNode? = null,
    block: suspend ToolScope.() -> ToolResult,
): TachyonServer =
    registerTool(configure = {
        name(name)
        description(description)
        inputSchema(inputSchema)
        outputSchema(outputSchema)
    }, block = block)

@JvmSynthetic
public fun TachyonServer.registerTool(
    name: String,
    description: String? = null,
    inputSchema: String,
    outputSchema: String? = null,
    block: suspend ToolScope.() -> ToolResult,
): TachyonServer =
    registerTool(configure = {
        name(name)
        description(description)
        schemas(inputSchema, outputSchema)
    }, block = block)

@JvmSynthetic
@OptIn(ExperimentalContracts::class)
public inline fun TachyonServer.registerTool(
    configure: ToolDescriptor.Builder.() -> Unit = {},
    noinline block: suspend ToolScope.() -> ToolResult,
): TachyonServer {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    return registerTool(
        descriptor = ToolDescriptor.builder().apply(configure).build(),
        block = block,
    )
}

@JvmSynthetic
public fun TachyonServer.registerTool(
    descriptor: ToolDescriptor,
    block: suspend ToolScope.() -> ToolResult,
): TachyonServer {
    this.tools().register(toolHandler(descriptor, block))
    return this
}

/**
 * Registers a tool using a [JsonObject] input schema.
 * Requires kotlinx-serialization-json on the classpath.
 */
@JvmSynthetic
public fun TachyonServer.registerTool(
    name: String,
    description: String? = null,
    inputSchema: JsonObject,
    outputSchema: JsonObject? = null,
    block: suspend ToolScope.() -> ToolResult,
): TachyonServer =
    registerTool(
        name = name,
        description = description,
        inputSchema = inputSchema.toJacksonNode(),
        outputSchema = outputSchema.toJacksonNodeOrNull(),
        block = block,
    )
