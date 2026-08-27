// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server

import dev.tachyonmcp.api.annotations.ExperimentalApi
import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.server.domain.Icon
import dev.tachyonmcp.api.server.domain.ToolAnnotations
import dev.tachyonmcp.api.server.features.tasks.TaskSupport
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor
import dev.tachyonmcp.api.server.features.tools.ToolResult
import dev.tachyonmcp.kotlin.server.config.ToolScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.kotlin.server.features.tools.toolDescriptorOf
import dev.tachyonmcp.kotlin.server.features.tools.toolFn
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
    /**
     * Registers a suspend tool handler.
     *
     * Accepts every optional attribute of [ToolDescriptor.Builder]; pass a prebuilt
     * [ToolDescriptor] instead when a descriptor is already at hand.
     */
    @JvmSynthetic
    public fun registerTool(
        name: String,
        description: String? = null,
        title: String? = null,
        inputSchema: JsonSchema? = null,
        outputSchema: JsonSchema? = null,
        taskSupport: TaskSupport? = null,
        annotations: ToolAnnotations? = null,
        icons: List<Icon>? = null,
        meta: Map<String, Any>? = null,
        block: suspend ToolScope.() -> ToolResult,
    ): TachyonServer =
        registerTool(
            descriptor =
                toolDescriptorOf(
                    name = name,
                    description = description,
                    title = title,
                    inputSchema = inputSchema,
                    outputSchema = outputSchema,
                    taskSupport = taskSupport,
                    annotations = annotations,
                    icons = icons,
                    meta = meta,
                ),
            block = block,
        )

    /** Registers a suspend tool handler using encoded JSON schemas. */
    @Suppress("LongParameterList", "MaxLineLength")
    @JvmSynthetic
    public fun registerTool(
        name: String,
        description: String? = null,
        title: String? = null,
        inputSchema: String,
        outputSchema: String? = null,
        taskSupport: TaskSupport? = null,
        annotations: ToolAnnotations? = null,
        icons: List<Icon>? = null,
        meta: Map<String, Any>? = null,
        block: suspend ToolScope.() -> ToolResult,
    ): TachyonServer =
        registerTool(
            name = name,
            description = description,
            title = title,
            inputSchema = JsonSchema.parse(inputSchema),
            outputSchema = outputSchema?.let(JsonSchema::parse),
            taskSupport = taskSupport,
            annotations = annotations,
            icons = icons,
            meta = meta,
            block = block,
        )

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
    @Suppress("LongParameterList")
    @JvmSynthetic
    public fun registerTool(
        name: String,
        description: String? = null,
        title: String? = null,
        inputSchema: JsonObject,
        outputSchema: JsonObject? = null,
        taskSupport: TaskSupport? = null,
        annotations: ToolAnnotations? = null,
        icons: List<Icon>? = null,
        meta: Map<String, Any>? = null,
        block: suspend ToolScope.() -> ToolResult,
    ): TachyonServer =
        registerTool(
            name = name,
            description = description,
            title = title,
            inputSchema = inputSchema.toJsonSchema(),
            outputSchema = outputSchema.toJsonSchemaOrNull(),
            taskSupport = taskSupport,
            annotations = annotations,
            icons = icons,
            meta = meta,
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

/**
 * Registers a suspend tool on an already-built server, resolving its input/output schemas from
 * [In]/[Out] and adapting the block to those types.
 *
 * The post-build twin of
 * [typedTool][dev.tachyonmcp.kotlin.server.config.TachyonServerBuilder.typedTool] — see its
 * documentation for how schemas are resolved through the
 * [dev.tachyonmcp.api.json.spi.JsonSchemaFactory] service chain, and note that registration
 * throws [IllegalStateException] when no factory in that chain produces a schema.
 *
 * Two behaviours go beyond the build-time overload, mirroring
 * [dev.tachyonmcp.api.server.features.tools.TypedToolFn]: the call arguments are decoded into
 * [In] by the serde configured in server config, and the block's return value is wrapped with
 * [ToolResult.structured]. The block runs with a [ToolScope] receiver, so `ctx`, `request`, and
 * `arguments` stay reachable.
 *
 * The block may return **either** shape:
 *  - an [Out] — wrapped into a success result carrying it as `structuredContent`;
 *  - a [ToolResult] — passed through untouched, for results that also need `_meta`, a custom
 *    text block, extra content blocks, [ToolScope.fail], or [ToolScope.inputRequired].
 *
 * The two never collide: [ToolResult] is a sealed interface, so no [Out] can also be one. A
 * value that is neither is a programming error and fails with [ClassCastException] naming the
 * expected type.
 *
 * Unlike the build-time `typedTool`, this can safely share the `registerTool` name: both type
 * arguments must always be given explicitly (neither is inferable from the block), and an
 * explicit type argument list excludes every untyped overload, which declares none. Existing
 * schema-less `registerTool(name) { }` calls therefore never resolve here, and omitting the type
 * arguments is a compile error rather than a silent fallback.
 *
 * ```kotlin
 * // plain payload
 * server.registerTool<GreetArgs, GreetReply>("greet") { args ->
 *     GreetReply("Hello, ${args.name}!")
 * }
 *
 * // full result — same overload
 * server.registerTool<GreetArgs, GreetReply>("greet") { args ->
 *     if (args.name.isBlank()) fail("name is required")
 *     else success(GreetReply("Hello, ${args.name}!"), text = "greeted")
 *         .withMeta("cached", false)
 * }
 * ```
 *
 * @param name tool name
 * @param description optional tool description
 * @param taskSupport optional task-augmentation support level
 * @param block block receiving the decoded [In], returning an [Out] or a [ToolResult]
 * @return this server
 */
@ExperimentalApi
@JvmSynthetic
@Suppress("LongParameterList")
public inline fun <reified In : Any, reified Out : Any> TachyonServer.registerTool(
    name: String,
    description: String? = null,
    title: String? = null,
    taskSupport: TaskSupport? = null,
    annotations: ToolAnnotations? = null,
    icons: List<Icon>? = null,
    meta: Map<String, Any>? = null,
    noinline block: suspend ToolScope.(In) -> Any,
): TachyonServer {
    val inputType = In::class.java
    val outputType = Out::class.java
    val descriptor =
        toolDescriptorOf(
            name = name,
            description = description,
            title = title,
            inputSchema = JsonSchema.generate(inputType),
            outputSchema = JsonSchema.generate(outputType),
            taskSupport = taskSupport,
            annotations = annotations,
            icons = icons,
            meta = meta,
        )
    return registerTool(descriptor) {
        when (val produced = block(arguments.decode(inputType))) {
            is ToolResult -> produced
            else -> success(outputType.cast(produced))
        }
    }
}
