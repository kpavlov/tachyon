// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.ServerBuilder
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.TachyonServer
import dev.tachyonmcp.server.domain.Annotations
import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.features.prompts.promptHandler
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry
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

        public fun tool(
            name: String,
            description: String? = null,
            inputSchema: JsonNode? = null,
            outputSchema: JsonNode? = null,
            handler: suspend ToolScope.() -> ToolResult,
        ): TachyonServerBuilder =
            this.also { delegate.tool(name, description, inputSchema, outputSchema, handler) }

        public fun tool(
            name: String,
            description: String? = null,
            inputSchema: String,
            outputSchema: String? = null,
            handler: suspend ToolScope.() -> ToolResult,
        ): TachyonServerBuilder =
            this.also { delegate.tool(name, description, inputSchema, outputSchema, handler) }

        public fun resource(
            name: String,
            uri: String,
            description: String? = null,
            mimeType: String? = null,
            handler: suspend ResourceScope.() -> ResourceContents,
        ): TachyonServerBuilder =
            this.also { delegate.resource(name, uri, description, mimeType, handler) }

        public fun prompt(
            name: String,
            description: String? = null,
            handler: suspend PromptScope.() -> List<PromptMessage>,
        ): TachyonServerBuilder =
            this.also {
                val descriptor = PromptDescriptor(name = name, description = description)
                delegate.prompt(descriptor, promptHandler(descriptor, handler))
            }

        public fun resourceTemplate(template: ResourceTemplateEntry): TachyonServerBuilder =
            this.also { delegate.resourceTemplate(template) }

        public fun resourceTemplate(
            name: String,
            uriTemplate: String,
            title: String? = null,
            description: String? = null,
            mimeType: String? = null,
            annotations: Annotations? = null,
            icons: List<Icon>? = null,
            block: suspend TemplateScope.() -> ResourceContents,
        ): TachyonServerBuilder =
            this.also {
                delegate.resourceTemplate(
                    name = name,
                    uriTemplate = uriTemplate,
                    title = title,
                    description = description,
                    mimeType = mimeType,
                    icons = icons,
                    annotations = annotations,
                    handler = block,
                )
            }

        /**
         * Registers a tool using a [kotlinx.serialization.json.JsonObject] input schema.
         * Requires kotlinx-serialization-json on the classpath.
         */
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
