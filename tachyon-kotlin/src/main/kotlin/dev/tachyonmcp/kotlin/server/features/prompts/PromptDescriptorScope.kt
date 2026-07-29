// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.features.prompts

import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.server.domain.Icon
import dev.tachyonmcp.api.server.domain.PromptArgument
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.kotlin.server.TachyonDsl
import dev.tachyonmcp.kotlin.server.domain.PromptArgument
import dev.tachyonmcp.kotlin.server.domain.PromptArgumentBuilder
import dev.tachyonmcp.kotlin.server.json.toJsonSchema
import kotlinx.serialization.json.JsonObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@TachyonDsl
public class PromptDescriptorScope
    @PublishedApi
    internal constructor() {
        public var name: String? = null
        public var description: String? = null
        public var title: String? = null
        public var arguments: List<PromptArgument>? = null
        public var inputSchema: JsonSchema? = null
        public var icons: List<Icon>? = null
        public var meta: Map<String, Any>? = null

        /** Sets the input schema from a kotlinx-serialization [JsonObject]. */
        public fun inputSchema(json: JsonObject) {
            inputSchema = json.toJsonSchema()
        }

        /** Adds a prebuilt prompt argument. */
        public fun argument(argument: PromptArgument) {
            arguments = arguments.orEmpty() + argument
        }

        /** Builds and adds a prompt argument. */
        @OptIn(ExperimentalContracts::class)
        public inline fun argument(block: PromptArgumentBuilder.() -> Unit) {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            argument(PromptArgument(block))
        }

        @PublishedApi
        internal fun build(): PromptDescriptor {
            val n = requireNotNull(name) { "PromptDescriptor.name is required" }
            return PromptDescriptor
                .builder()
                .name(n)
                .description(description)
                .title(title)
                .arguments(arguments)
                .inputSchema(inputSchema)
                .icons(icons)
                .meta(meta)
                .build()
        }
    }

/** Builds a [PromptDescriptor] with a receiver DSL. */
@OptIn(ExperimentalContracts::class)
public inline fun PromptDescriptor(block: PromptDescriptorScope.() -> Unit): PromptDescriptor {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return PromptDescriptorScope().apply(block).build()
}
