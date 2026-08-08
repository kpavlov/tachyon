// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.api.annotations.ExperimentalApi
import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.server.domain.Annotations
import dev.tachyonmcp.api.server.domain.Icon
import dev.tachyonmcp.api.server.domain.PromptMessage
import dev.tachyonmcp.api.server.domain.ResourceContents
import dev.tachyonmcp.api.server.extensions.ServerExtension
import dev.tachyonmcp.api.server.features.completions.CompletionResult
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.api.server.features.tasks.TaskSupport
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor
import dev.tachyonmcp.api.server.features.tools.ToolResult
import dev.tachyonmcp.core.server.ServerBuilder
import dev.tachyonmcp.core.server.config.NetworkConfig
import dev.tachyonmcp.core.server.features.resources.MimeTypes
import dev.tachyonmcp.kotlin.server.DefaultKotlinTachyonServer
import dev.tachyonmcp.kotlin.server.TachyonDsl
import dev.tachyonmcp.kotlin.server.TachyonServer
import dev.tachyonmcp.kotlin.server.features.CoroutineRuntime
import dev.tachyonmcp.kotlin.server.json.toJsonSchema
import dev.tachyonmcp.kotlin.server.json.toJsonSchemaOrNull
import io.netty.channel.ChannelPipeline
import kotlinx.serialization.json.JsonObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import dev.tachyonmcp.core.server.TachyonServer as CoreTachyonServer

@TachyonDsl
public class TachyonServerBuilder
    @PublishedApi
    internal constructor() {
        @PublishedApi
        internal val delegate: ServerBuilder = CoreTachyonServer.builder()

        @PublishedApi
        internal var networkPortExplicitlySet: Boolean = false

        private val coroutineRuntime: CoroutineRuntime =
            CoroutineRuntime().also { delegate.withExtensions(it) }

        private val featureRegistrar: KotlinFeatureRegistrar =
            KotlinFeatureRegistrar(delegate, coroutineRuntime)

        @OptIn(ExperimentalContracts::class)
        public inline fun info(
            crossinline configure: (@TachyonDsl ServerInfoScope).() -> Unit,
        ): TachyonServerBuilder {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            val scope = ServerInfoScope()
            scope.configure()
            delegate.info { scope.applyTo(it) }
            return this
        }

        @OptIn(ExperimentalContracts::class)
        public inline fun capabilities(
            crossinline configure: (@TachyonDsl CapabilitiesScope).() -> Unit,
        ): TachyonServerBuilder {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            val scope = CapabilitiesScope()
            scope.configure()
            delegate.capabilities { scope.applyTo(it) }
            return this
        }

        @OptIn(ExperimentalContracts::class)
        public inline fun network(
            crossinline configure: (@TachyonDsl NetworkScope).() -> Unit,
        ): TachyonServerBuilder {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            val scope = NetworkScope()
            scope.configure()
            delegate.network { scope.applyTo(it) }
            if (scope.port != null) {
                networkPortExplicitlySet = true
            }
            return this
        }

        @OptIn(ExperimentalContracts::class)
        public inline fun session(
            crossinline configure: (@TachyonDsl SessionScope).() -> Unit,
        ): TachyonServerBuilder {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            val scope = SessionScope()
            scope.configure()
            delegate.session { scope.applyTo(it) }
            return this
        }

        @OptIn(ExperimentalContracts::class)
        public inline fun runtime(
            crossinline configure: (@TachyonDsl RuntimeScope).() -> Unit,
        ): TachyonServerBuilder {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            val scope = RuntimeScope()
            scope.configure()
            delegate.runtime { scope.applyTo(it) }
            return this
        }

        @OptIn(ExperimentalContracts::class)
        public inline fun monitoring(
            crossinline configure: (@TachyonDsl MonitoringScope).() -> Unit,
        ): TachyonServerBuilder {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            val scope = MonitoringScope()
            scope.configure()
            delegate.monitoring { scope.applyTo(it) }
            return this
        }

        @JvmSynthetic
        public fun tool(
            name: String,
            description: String? = null,
            inputSchema: JsonSchema? = null,
            outputSchema: JsonSchema? = null,
            taskSupport: TaskSupport? = null,
            handler: suspend ToolScope.() -> ToolResult,
        ): TachyonServerBuilder =
            this.also {
                featureRegistrar.tool(
                    name,
                    description,
                    inputSchema,
                    outputSchema,
                    taskSupport,
                    handler,
                )
            }

        @JvmSynthetic
        @Suppress("MaxLineLength")
        @Deprecated(
            level = DeprecationLevel.WARNING,
            message = "Use tool(...) with explicit JsonSchema object",
            replaceWith =
                ReplaceWith(
                    "tool(name, description, JsonSchema.parse(inputSchema), outputSchema?.let(JsonSchema::parse), taskSupport, handler)",
                    "dev.tachyonmcp.api.json.JsonSchema",
                ),
        )
        public fun tool(
            name: String,
            description: String? = null,
            inputSchema: String,
            outputSchema: String? = null,
            taskSupport: TaskSupport? = null,
            handler: suspend ToolScope.() -> ToolResult,
        ): TachyonServerBuilder =
            this.also {
                featureRegistrar.tool(
                    name,
                    description,
                    inputSchema,
                    outputSchema,
                    taskSupport,
                    handler,
                )
            }

        /**
         * Registers a tool whose input/output schemas are resolved from [In]/[Out] via
         * [dev.tachyonmcp.api.json.JsonSchema.generate], which tries the service-loaded
         * [dev.tachyonmcp.api.json.spi.JsonSchemaFactory] chain in ascending priority order: a
         * build-time codegen resource (tachyon-core's `KtSchemaResourceFactory`) wins when
         * present, otherwise the runtime reflection generator in the `tachyon-kotlin-kt-schema`
         * integration artifact (its `KtSchemaReflectionFactory`, registered via
         * `META-INF/services`) back-fills. Add that artifact to the classpath to use `typedTool`
         * without a codegen resource. To use a different generator, register your own
         * `JsonSchemaFactory<Class<?>>` via `META-INF/services` — `json { schemaFactory = ... }`
         * configures the unrelated *validation* factory (`sourceType() == String`) and has no
         * effect here. Throws [IllegalStateException] at registration time if no factory in the
         * chain produces a schema.
         *
         * Named `typedTool` rather than an overload of `tool` because a same-named reified
         * overload wins Kotlin's overload resolution for existing schema-less `tool(name) { }`
         * calls too, then fails to infer [In]/[Out] there — breaking every such call site.
         */
        @ExperimentalApi
        @JvmSynthetic
        public inline fun <reified In : Any, reified Out : Any> typedTool(
            name: String,
            description: String? = null,
            taskSupport: TaskSupport? = null,
            noinline handler: suspend ToolScope.() -> ToolResult,
        ): TachyonServerBuilder =
            typedToolFor(
                inputType = In::class.java,
                outputType = Out::class.java,
                name = name,
                description = description,
                taskSupport = taskSupport,
                handler = handler,
            )

        @PublishedApi
        internal fun typedToolFor(
            inputType: Class<*>,
            outputType: Class<*>,
            name: String,
            description: String?,
            taskSupport: TaskSupport?,
            handler: suspend ToolScope.() -> ToolResult,
        ): TachyonServerBuilder =
            this.also {
                featureRegistrar.tool(
                    name,
                    description,
                    JsonSchema.generate(inputType),
                    JsonSchema.generate(outputType),
                    taskSupport,
                    handler,
                )
            }

        /**
         * Registers a prebuilt tool descriptor with a suspending handler block.
         *
         * @param descriptor tool descriptor
         * @param handler handler invoked for tool calls
         * @return this builder
         */
        @JvmSynthetic
        public fun tool(
            descriptor: ToolDescriptor,
            handler: suspend ToolScope.() -> ToolResult,
        ): TachyonServerBuilder = this.also { featureRegistrar.tool(descriptor, handler) }

        /**
         * Registers a static resource with a suspending handler block.
         *
         * @param name resource name
         * @param uri resource URI
         * @param description optional resource description
         * @param mimeType resource MIME type; defaults to a guess from `uri`'s extension
         * @param title optional human-readable title
         * @param annotations optional presentation hints
         * @param size optional raw content size in bytes
         * @param icons optional associated icons
         * @param block handles reads of the registered resource
         * @return this builder
         */
        @JvmSynthetic
        public fun resource(
            name: String,
            uri: String,
            description: String? = null,
            mimeType: String? = MimeTypes.guess(uri),
            title: String? = null,
            annotations: Annotations? = null,
            size: Long? = null,
            icons: List<Icon>? = null,
            block: suspend ResourceScope.() -> ResourceContents,
        ): TachyonServerBuilder =
            this.also {
                featureRegistrar.resource(
                    name = name,
                    uri = uri,
                    description = description,
                    mimeType = mimeType,
                    title = title,
                    annotations = annotations,
                    size = size,
                    icons = icons,
                    block = block,
                )
            }

        /**
         * Registers a prebuilt static-resource descriptor.
         *
         * @param descriptor static-resource descriptor
         * @param block handler invoked for resource reads
         * @return this builder
         */
        @JvmSynthetic
        public fun resource(
            descriptor: ResourceDescriptor,
            block: suspend ResourceScope.() -> ResourceContents,
        ): TachyonServerBuilder = this.also { featureRegistrar.resource(descriptor, block) }

        /**
         * Registers a prompt with the server.
         *
         * @param name The prompt name.
         * @param description An optional description of the prompt.
         * @param handler The handler that generates the prompt messages.
         * @return This builder.
         */
        @JvmSynthetic
        public fun prompt(
            name: String,
            description: String? = null,
            handler: suspend PromptScope.() -> List<PromptMessage>,
        ): TachyonServerBuilder =
            this.also {
                val descriptor = PromptDescriptor.of(name, description, null, null, null)
                featureRegistrar.prompt(descriptor, handler)
            }

        /**
         * Registers a prebuilt prompt descriptor with a suspending handler block.
         *
         * @param descriptor prompt descriptor
         * @param handler handler invoked for prompt requests
         * @return this builder
         */
        @JvmSynthetic
        public fun prompt(
            descriptor: PromptDescriptor,
            handler: suspend PromptScope.() -> List<PromptMessage>,
        ): TachyonServerBuilder = this.also { featureRegistrar.prompt(descriptor, handler) }

        /**
         * Registers a resource template with the server.
         *
         * @param name The template name.
         * @param uriTemplate The URI template used to identify resources.
         * @param description The optional template description.
         * @param mimeType The optional MIME type of the resources.
         * @param title The optional template title.
         * @param annotations The optional template annotations.
         * @param icons The optional template icons.
         * @param block Handles requests for resources matching the template.
         * @return This builder.
         */
        @JvmSynthetic
        public fun resourceTemplate(
            name: String,
            uriTemplate: String,
            description: String? = null,
            mimeType: String? = null,
            title: String? = null,
            annotations: Annotations? = null,
            icons: List<Icon>? = null,
            block: suspend TemplateScope.() -> ResourceContents,
        ): TachyonServerBuilder =
            this.also {
                featureRegistrar.resourceTemplate(
                    name = name,
                    uriTemplate = uriTemplate,
                    description = description,
                    mimeType = mimeType,
                    title = title,
                    annotations = annotations,
                    icons = icons,
                    block = block,
                )
            }

        /**
         * Registers a prebuilt resource-template descriptor.
         *
         * @param descriptor resource-template descriptor
         * @param block handler invoked for matching resource requests
         * @return This builder.
         */
        @JvmSynthetic
        public fun resourceTemplate(
            descriptor: ResourceTemplateDescriptor,
            block: suspend TemplateScope.() -> ResourceContents,
        ): TachyonServerBuilder = this.also { featureRegistrar.resourceTemplate(descriptor, block) }

        /**
         * Registers a completion handler for a prompt's arguments.
         *
         * @param promptName the prompt name
         * @param handler the suspend function that returns completion candidates
         * @return this builder
         */
        @JvmSynthetic
        public fun promptCompletion(
            promptName: String,
            handler: suspend CompletionScope.() -> CompletionResult,
        ): TachyonServerBuilder =
            this.also {
                featureRegistrar.promptCompletion(promptName, handler)
            }

        /**
         * Registers a completion handler for a resource template's variables.
         *
         * @param uriOrTemplate the resource URI or template
         * @param handler the suspend function that returns completion candidates
         * @return this builder
         */
        @JvmSynthetic
        public fun resourceCompletion(
            uriOrTemplate: String,
            handler: suspend CompletionScope.() -> CompletionResult,
        ): TachyonServerBuilder =
            this.also {
                featureRegistrar.resourceCompletion(uriOrTemplate, handler)
            }

        /**
         * Registers a tool using a [JsonObject] input schema.
         */
        @JvmSynthetic
        public fun tool(
            name: String,
            description: String? = null,
            inputSchema: JsonObject,
            outputSchema: JsonObject? = null,
            taskSupport: TaskSupport? = null,
            handler: suspend ToolScope.() -> ToolResult,
        ): TachyonServerBuilder =
            this.tool(
                name = name,
                description = description,
                inputSchema = inputSchema.toJsonSchema(),
                outputSchema = outputSchema.toJsonSchemaOrNull(),
                taskSupport = taskSupport,
                handler = handler,
            )

        public fun name(name: String): TachyonServerBuilder = this.also { delegate.name(name) }

        /** Registers one or more [ServerExtension]s, e.g. from `tachyon-extensions`. */
        public fun extensions(vararg extensions: ServerExtension): TachyonServerBuilder =
            this.also { delegate.withExtensions(*extensions) }

        /** Configures the JSON payload boundary: serde, schema factory, and validators. */
        @OptIn(ExperimentalContracts::class)
        public inline fun json(
            crossinline configure: (@TachyonDsl JsonScope).() -> Unit,
        ): TachyonServerBuilder {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            JsonScope().apply(configure).applyTo(delegate)
            return this
        }

        @ExperimentalApi
        public fun pipelineCustomizer(
            customizer: (@TachyonDsl ChannelPipeline).() -> Unit,
        ): TachyonServerBuilder = this.also { delegate.pipelineCustomizer { it.customizer() } }

        @PublishedApi
        internal fun applyPort(port: Int?): TachyonServerBuilder =
            this.also {
                if (port != null) {
                    delegate.port(port)
                } else if (!networkPortExplicitlySet) {
                    delegate.port(NetworkConfig.UNSET_PORT)
                }
            }

        @PublishedApi
        internal fun start(): TachyonServer = build().also { it.start() }

        @PublishedApi
        internal fun build(): TachyonServer =
            DefaultKotlinTachyonServer(delegate.build(), coroutineRuntime)
    }
