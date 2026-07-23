// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.ServerBuilder
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.TachyonServer
import dev.tachyonmcp.server.domain.Annotations
import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.completions.CompletionResult
import dev.tachyonmcp.server.features.completions.promptCompletionHandler
import dev.tachyonmcp.server.features.completions.resourceCompletionHandler
import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.features.prompts.promptHandler
import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.resources.ResourceTemplateDescriptor
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.json.KxSerializationSerde
import dev.tachyonmcp.server.json.toJacksonNode
import dev.tachyonmcp.server.json.toJacksonNodeOrNull
import io.netty.channel.ChannelPipeline
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@TachyonDsl
public class TachyonServerBuilder
    @PublishedApi
    internal constructor() {
        @PublishedApi
        internal val delegate: ServerBuilder =
            TachyonServer.builder().also {
                it.json { config -> config.serde(KxSerializationSerde.Default) }
            }

        @PublishedApi
        internal var networkPortExplicitlySet: Boolean = false

        private val featureRegistrar: KotlinFeatureRegistrar = KotlinFeatureRegistrar(delegate)

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
            inputSchema: JsonNode? = null,
            outputSchema: JsonNode? = null,
            handler: suspend ToolScope.() -> ToolResult,
        ): TachyonServerBuilder =
            this.also {
                featureRegistrar.tool(
                    name,
                    description,
                    inputSchema,
                    outputSchema,
                    handler,
                )
            }

        @JvmSynthetic
        public fun tool(
            name: String,
            description: String? = null,
            inputSchema: String,
            outputSchema: String? = null,
            handler: suspend ToolScope.() -> ToolResult,
        ): TachyonServerBuilder =
            this.also {
                featureRegistrar.tool(
                    name,
                    description,
                    inputSchema,
                    outputSchema,
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
         * @param mimeType optional resource MIME type
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
            mimeType: String? = null,
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
                val descriptor = PromptDescriptor(name = name, description = description)
                delegate.prompt(descriptor, promptHandler(descriptor, handler))
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
         * @param title The optional template title.
         * @param description The optional template description.
         * @param mimeType The optional MIME type of the resources.
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
                delegate.promptCompletion(promptName, promptCompletionHandler(promptName, handler))
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
                delegate.resourceCompletion(
                    uriOrTemplate,
                    resourceCompletionHandler(uriOrTemplate, handler),
                )
            }

        /**
         * Registers a tool using a [kotlinx.serialization.json.JsonObject] input schema.
         * Requires kotlinx-serialization-json on the classpath.
         */
        @JvmSynthetic
        public fun tool(
            name: String,
            description: String? = null,
            inputSchema: JsonObject,
            outputSchema: JsonObject? = null,
            handler: suspend ToolScope.() -> ToolResult,
        ): TachyonServerBuilder =
            this.tool(
                name = name,
                description = description,
                inputSchema = inputSchema.toJacksonNode(),
                outputSchema = outputSchema.toJacksonNodeOrNull(),
                handler = handler,
            )

        public fun name(name: String): TachyonServerBuilder = this.also { delegate.name(name) }

        /** Configures the JSON payload boundary: serde and input/output schema validators. */
        @OptIn(ExperimentalContracts::class)
        public inline fun json(
            crossinline configure: (@TachyonDsl JsonScope).() -> Unit,
        ): TachyonServerBuilder {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            JsonScope().apply(configure).applyTo(delegate)
            return this
        }

        public fun pipelineCustomizer(
            customizer: (@TachyonDsl ChannelPipeline).() -> Unit,
        ): TachyonServerBuilder = this.also { delegate.pipelineCustomizer { it.customizer() } }

        @PublishedApi
        internal fun applyPort(port: Int?): TachyonServerBuilder =
            this.also {
                if (port != null) {
                    delegate.port(port)
                } else if (!networkPortExplicitlySet) {
                    delegate.port(NetworkConfig.DEFAULT.port())
                }
            }

        @PublishedApi
        internal fun start(): TachyonServer = delegate.start()

        @PublishedApi
        internal fun build(): TachyonServer = delegate.build()
    }
