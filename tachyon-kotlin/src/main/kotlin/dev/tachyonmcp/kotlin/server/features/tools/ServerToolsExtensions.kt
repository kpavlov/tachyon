// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.tools

import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor
import dev.tachyonmcp.api.server.features.tools.ToolResult
import dev.tachyonmcp.core.server.TachyonServer
import dev.tachyonmcp.kotlin.server.config.ToolScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.kotlin.server.json.schemas
import dev.tachyonmcp.kotlin.server.json.toJsonSchema
import dev.tachyonmcp.kotlin.server.json.toJsonSchemaOrNull
import kotlinx.serialization.json.JsonObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@JvmSynthetic
public fun TachyonServer.registerTool(
    name: String,
    description: String? = null,
    inputSchema: JsonSchema? = null,
    outputSchema: JsonSchema? = null,
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
        descriptor =
            ToolDescriptor
                .builder()
                .apply(
                    configure,
                ).build(),
        block = block,
    )
}

/**
 * Registers a tool with the server.
 *
 * @param descriptor The descriptor defining the tool.
 * @param block The handler invoked when the tool is called.
 * @return This server instance.
 */
@JvmSynthetic
public fun TachyonServer.registerTool(
    descriptor: ToolDescriptor,
    block: suspend ToolScope.() -> ToolResult,
): TachyonServer {
    val runtime =
        extensions().filterIsInstance<CoroutineRuntime>().singleOrNull()
            ?: error("registerTool requires a server built with the Tachyon Kotlin DSL")
    this.tools().registerAsync(descriptor, toolFn(descriptor.name(), runtime, block))
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
        inputSchema = inputSchema.toJsonSchema(),
        outputSchema = outputSchema.toJsonSchemaOrNull(),
        block = block,
    )
