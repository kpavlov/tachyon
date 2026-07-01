// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.domain.ResourceContents
import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.features.resources.ResourceDescriptor
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import io.netty.channel.ChannelPipeline
import tools.jackson.databind.JsonNode
import java.util.concurrent.CompletableFuture

@TachyonDsl
public class TachyonServerBuilder
    @PublishedApi
    internal constructor() {
        @PublishedApi
        internal val delegate: ServerBuilder = TachyonServer.builder()

        @PublishedApi
        internal var networkPortExplicitlySet: Boolean = false

        public inline fun info(
            crossinline configure: (@TachyonDsl ServerInfoScope).() -> Unit,
        ): TachyonServerBuilder {
            delegate.info { ServerInfoScope().apply(configure).applyTo(it) }
            return this
        }

        public inline fun capabilities(
            crossinline configure: (@TachyonDsl CapabilitiesScope).() -> Unit,
        ): TachyonServerBuilder {
            delegate.capabilities { CapabilitiesScope().apply(configure).applyTo(it) }
            return this
        }

        public inline fun network(
            crossinline configure: (@TachyonDsl NetworkScope).() -> Unit,
        ): TachyonServerBuilder {
            val scope = NetworkScope()
            scope.configure()
            delegate.network { scope.applyTo(it) }
            if (scope.port != null) {
                networkPortExplicitlySet = true
            }
            return this
        }

        public inline fun session(
            crossinline configure: (@TachyonDsl SessionScope).() -> Unit,
        ): TachyonServerBuilder {
            delegate.session { SessionScope().apply(configure).applyTo(it) }
            return this
        }

        public fun tool(
            name: String,
            description: String? = null,
            inputSchema: JsonNode? = null,
            handler: suspend ToolScope.() -> ToolResult<*>,
        ): TachyonServerBuilder =
            this.also {
                delegate.tool(
                    asyncHandler(
                        ToolDescriptor
                            .builder(name)
                            .description(description)
                            .inputSchema(inputSchema)
                            .build(),
                        handler,
                    ),
                )
            }

        public fun resource(
            name: String,
            uri: String,
            description: String? = null,
            mimeType: String = "application/json",
            handler: ResourceScope.() -> ResourceContents,
        ): TachyonServerBuilder =
            this.also {
                delegate.resource(
                    ResourceDescriptor.of(name, uri, description, mimeType),
                ) { ctx, req ->
                    ResourceScope(ctx, req).handler()
                }
            }

        public fun prompt(
            name: String,
            description: String,
            handler: (arguments: String?) -> List<PromptMessage>,
        ): TachyonServerBuilder =
            this.also {
                delegate.prompt(PromptDescriptor.of(name, description)) { args -> handler(args) }
            }

        public fun name(name: String): TachyonServerBuilder = this.also { delegate.name(name) }

        public fun pipelineCustomizer(
            customizer: (@TachyonDsl ChannelPipeline).() -> Unit,
        ): TachyonServerBuilder = this.also { delegate.pipelineCustomizer { it.customizer() } }

        @PublishedApi internal fun applyPort(port: Int?): TachyonServerBuilder =
            this.also {
                if (port != null) {
                    delegate.port(port)
                } else if (!networkPortExplicitlySet) {
                    delegate.port(8080)
                }
            }

        @PublishedApi internal fun start(): McpServerHandle = delegate.start()

        @PublishedApi internal fun startAsync(): CompletableFuture<McpServerHandle> =
            delegate
                .startAsync()

        @PublishedApi internal fun build(): McpServer = delegate.build()
    }
