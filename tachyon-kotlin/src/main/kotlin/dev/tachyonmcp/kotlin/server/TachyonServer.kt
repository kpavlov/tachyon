// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server

import dev.tachyonmcp.api.annotations.ExperimentalApi
import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.server.domain.Annotations
import dev.tachyonmcp.api.server.domain.Icon
import dev.tachyonmcp.api.server.domain.PromptArgument
import dev.tachyonmcp.api.server.domain.PromptMessage
import dev.tachyonmcp.api.server.domain.ResourceContents
import dev.tachyonmcp.api.server.domain.ToolAnnotations
import dev.tachyonmcp.api.server.features.completions.CompletionResult
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.api.server.features.tasks.TaskSupport
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor
import dev.tachyonmcp.api.server.features.tools.ToolResult
import dev.tachyonmcp.core.server.features.resources.MimeTypes
import dev.tachyonmcp.kotlin.server.config.CompletionScope
import dev.tachyonmcp.kotlin.server.config.PromptScope
import dev.tachyonmcp.kotlin.server.config.ResourceScope
import dev.tachyonmcp.kotlin.server.config.TemplateScope
import dev.tachyonmcp.kotlin.server.config.ToolScope
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.kotlin.server.features.completions.promptCompletionFn
import dev.tachyonmcp.kotlin.server.features.completions.resourceCompletionFn
import dev.tachyonmcp.kotlin.server.features.prompts.promptFn
import dev.tachyonmcp.kotlin.server.features.resources.resourceFn
import dev.tachyonmcp.kotlin.server.features.resources.templateFn
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
@Suppress("TooManyFunctions")
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

    /**
     * Registers a static resource with a suspend handler, accepting every optional attribute of
     * [ResourceDescriptor.Builder]; pass a prebuilt [ResourceDescriptor] instead when a descriptor
     * is already at hand.
     *
     * @param name resource name; need not be unique — [uri] is the resource's identity
     * @param uri resource URI
     * @param description optional resource description
     * @param mimeType resource MIME type, guessed from [uri]'s extension by default
     * @param title optional human-readable title
     * @param annotations optional resource annotations
     * @param size optional raw content size in bytes
     * @param icons associated icons, or an empty list
     * @param meta optional protocol extension metadata
     * @param block handles reads of the registered resource
     * @return this server
     * @throws IllegalArgumentException if [uri] is already registered under a different name
     */
    @JvmSynthetic
    @Suppress("LongParameterList")
    public fun registerResource(
        name: String,
        uri: String,
        description: String? = null,
        mimeType: String? = MimeTypes.guess(uri),
        title: String? = null,
        annotations: Annotations? = null,
        size: Long? = null,
        icons: List<Icon> = emptyList(),
        meta: Map<String, Any>? = null,
        block: suspend ResourceScope.() -> ResourceContents,
    ): TachyonServer =
        registerResource(
            descriptor =
                ResourceDescriptor
                    .builder()
                    .name(name)
                    .uri(uri)
                    .description(description)
                    .mimeType(mimeType)
                    .title(title)
                    .annotations(annotations)
                    .size(size)
                    .icons(icons)
                    .meta(meta)
                    .build(),
            block = block,
        )

    /**
     * Registers a prebuilt static-resource descriptor with a suspend handler.
     *
     * Callable before or after [start]. Re-registering the same URI under the same name replaces
     * the resource in place; registering it under a different name is rejected. Does nothing when
     * the resources capability is off — the call still returns this server.
     *
     * @param descriptor static-resource descriptor
     * @param block handler invoked for resource reads
     * @return this server
     * @throws IllegalArgumentException if the descriptor's URI is already registered under a
     * different name
     */
    @JvmSynthetic
    public fun registerResource(
        descriptor: ResourceDescriptor,
        block: suspend ResourceScope.() -> ResourceContents,
    ): TachyonServer

    /**
     * Registers a resource template with a suspend handler, accepting every optional attribute of
     * [ResourceTemplateDescriptor.Builder]; pass a prebuilt [ResourceTemplateDescriptor] instead
     * when a descriptor is already at hand.
     *
     * @param name template name; must be unique among registered templates
     * @param uriTemplate URI template used to match resource requests
     * @param description optional template description
     * @param mimeType optional MIME type of the matched resources
     * @param title optional human-readable title
     * @param annotations optional template annotations
     * @param icons associated icons, or an empty list
     * @param meta optional protocol extension metadata
     * @param block handles requests for resources matching the template
     * @return this server
     * @throws IllegalArgumentException if a template is already registered under [name]
     */
    @JvmSynthetic
    @Suppress("LongParameterList")
    public fun registerResourceTemplate(
        name: String,
        uriTemplate: String,
        description: String? = null,
        mimeType: String? = null,
        title: String? = null,
        annotations: Annotations? = null,
        icons: List<Icon> = emptyList(),
        meta: Map<String, Any>? = null,
        block: suspend TemplateScope.() -> ResourceContents,
    ): TachyonServer =
        registerResourceTemplate(
            descriptor =
                ResourceTemplateDescriptor
                    .builder()
                    .name(name)
                    .uriTemplate(uriTemplate)
                    .description(description)
                    .mimeType(mimeType)
                    .title(title)
                    .annotations(annotations)
                    .icons(icons)
                    .meta(meta)
                    .build(),
            block = block,
        )

    /**
     * Registers a prebuilt resource-template descriptor with a suspend handler.
     *
     * Callable before or after [start]. Unlike the other `register*` functions this one rejects
     * duplicates outright rather than replacing — unregister the template first to swap its
     * handler. Does nothing when the resources capability is off — the call still returns this
     * server.
     *
     * @param descriptor resource-template descriptor
     * @param block handler invoked for matching resource requests
     * @return this server
     * @throws IllegalArgumentException if a template is already registered under the descriptor's
     * name
     */
    @JvmSynthetic
    public fun registerResourceTemplate(
        descriptor: ResourceTemplateDescriptor,
        block: suspend TemplateScope.() -> ResourceContents,
    ): TachyonServer

    /**
     * Registers a prompt with a suspend handler, accepting every optional attribute of
     * [PromptDescriptor.Builder]; pass a prebuilt [PromptDescriptor] instead when a descriptor is
     * already at hand.
     *
     * @param name prompt name; registering an existing name replaces that prompt
     * @param description optional prompt description
     * @param title optional human-readable title
     * @param arguments arguments accepted by this prompt, or an empty list
     * @param inputSchema optional JSON schema describing the prompt's arguments
     * @param icons associated icons, or an empty list
     * @param meta optional protocol extension metadata
     * @param block handler that generates the prompt messages
     * @return this server
     */
    @JvmSynthetic
    @Suppress("LongParameterList")
    public fun registerPrompt(
        name: String,
        description: String? = null,
        title: String? = null,
        arguments: List<PromptArgument> = emptyList(),
        inputSchema: JsonSchema? = null,
        icons: List<Icon> = emptyList(),
        meta: Map<String, Any>? = null,
        block: suspend PromptScope.() -> List<PromptMessage>,
    ): TachyonServer =
        registerPrompt(
            descriptor =
                PromptDescriptor
                    .builder()
                    .name(name)
                    .description(description)
                    .title(title)
                    .arguments(arguments)
                    .inputSchema(inputSchema)
                    .icons(icons)
                    .meta(meta)
                    .build(),
            block = block,
        )

    /**
     * Registers a prebuilt prompt descriptor with a suspend handler.
     *
     * Callable before or after [start]. Registering a name that already exists silently replaces
     * that prompt. Does nothing when the prompts capability is off — the call still returns this
     * server.
     *
     * @param descriptor prompt descriptor
     * @param block handler invoked for prompt requests
     * @return this server
     */
    @JvmSynthetic
    public fun registerPrompt(
        descriptor: PromptDescriptor,
        block: suspend PromptScope.() -> List<PromptMessage>,
    ): TachyonServer

    /**
     * Registers a completion handler for a prompt's arguments.
     *
     * Callable before or after [start]. Registering a prompt name that already has a handler
     * silently replaces it. Does nothing when the completions capability is off — the call still
     * returns this server.
     *
     * @param promptName the prompt name
     * @param block the suspend function that returns completion candidates
     * @return this server
     */
    @JvmSynthetic
    public fun registerPromptCompletion(
        promptName: String,
        block: suspend CompletionScope.() -> CompletionResult,
    ): TachyonServer

    /**
     * Registers a completion handler for a resource template's variables.
     *
     * Callable before or after [start]. [uriOrTemplate] is matched verbatim against the URI the
     * client sends, so it need not name a registered resource. Registering a URI or template that
     * already has a handler silently replaces it. Does nothing when the completions capability is
     * off — the call still returns this server.
     *
     * @param uriOrTemplate the resource URI or template
     * @param block the suspend function that returns completion candidates
     * @return this server
     */
    @JvmSynthetic
    public fun registerResourceCompletion(
        uriOrTemplate: String,
        block: suspend CompletionScope.() -> CompletionResult,
    ): TachyonServer
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

    override fun registerResource(
        descriptor: ResourceDescriptor,
        block: suspend ResourceScope.() -> ResourceContents,
    ): TachyonServer {
        resources().registerAsync(descriptor, resourceFn(descriptor, coroutineRuntime, block))
        return this
    }

    override fun registerResourceTemplate(
        descriptor: ResourceTemplateDescriptor,
        block: suspend TemplateScope.() -> ResourceContents,
    ): TachyonServer {
        resources().registerTemplateAsync(
            descriptor,
            templateFn(descriptor, coroutineRuntime, block),
        )
        return this
    }

    override fun registerPrompt(
        descriptor: PromptDescriptor,
        block: suspend PromptScope.() -> List<PromptMessage>,
    ): TachyonServer {
        prompts().registerAsync(descriptor, promptFn(descriptor, coroutineRuntime, block))
        return this
    }

    override fun registerPromptCompletion(
        promptName: String,
        block: suspend CompletionScope.() -> CompletionResult,
    ): TachyonServer {
        completions().registerForPromptAsync(
            promptName,
            promptCompletionFn(promptName, coroutineRuntime, block),
        )
        return this
    }

    override fun registerResourceCompletion(
        uriOrTemplate: String,
        block: suspend CompletionScope.() -> CompletionResult,
    ): TachyonServer {
        completions()
            .registerForResourceAsync(
                uriOrTemplate,
                resourceCompletionFn(uriOrTemplate, coroutineRuntime, block),
            )
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
