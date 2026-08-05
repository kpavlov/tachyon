// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server

import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor
import dev.tachyonmcp.api.server.features.tools.ToolResult
import dev.tachyonmcp.kotlin.server.config.ToolScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.kotlin.server.features.tools.toolFn
import dev.tachyonmcp.kotlin.server.json.schemas
import dev.tachyonmcp.kotlin.server.json.toJsonSchema
import dev.tachyonmcp.kotlin.server.json.toJsonSchemaOrNull
import kotlinx.serialization.json.JsonObject
import dev.tachyonmcp.core.server.TachyonServer as CoreTachyonServer

/**
 * Kotlin-owned Tachyon server with lifecycle-bound coroutine support.
 *
 * Extends the core [CoreTachyonServer] API with suspend post-build registration.
 * Instances are created by [TachyonServer] and [buildServer].
 */
public sealed interface TachyonServer : CoreTachyonServer {
    /** Registers a suspend tool handler. */
    @JvmSynthetic
    public fun registerTool(
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

    /** Registers a suspend tool handler using encoded JSON schemas. */
    @JvmSynthetic
    public fun registerTool(
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

    /** Registers a suspend tool handler configured through a descriptor builder. */
    @JvmSynthetic
    public fun registerTool(
        configure: ToolDescriptor.Builder.() -> Unit = {},
        block: suspend ToolScope.() -> ToolResult,
    ): TachyonServer =
        registerTool(
            descriptor =
                ToolDescriptor
                    .builder()
                    .apply(
                        configure,
                    ).build(),
            block = block,
        )

    /**
     * Registers a prebuilt tool descriptor with a suspend handler.
     *
     * @param descriptor tool descriptor
     * @param block handler invoked for tool calls
     * @return this server
     */
    @JvmSynthetic
    public fun registerTool(
        descriptor: ToolDescriptor,
        block: suspend ToolScope.() -> ToolResult,
    ): TachyonServer

    /**
     * Registers a suspend tool handler using kotlinx JSON schemas.
     */
    @JvmSynthetic
    public fun registerTool(
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
}

internal class DefaultKotlinTachyonServer(
    delegate: CoreTachyonServer,
    private val coroutineRuntime: CoroutineRuntime,
) : TachyonServer,
    CoreTachyonServer by delegate {
    override fun registerTool(
        descriptor: ToolDescriptor,
        block: suspend ToolScope.() -> ToolResult,
    ): TachyonServer {
        tools().registerAsync(descriptor, toolFn(descriptor.name(), coroutineRuntime, block))
        return this
    }
}
