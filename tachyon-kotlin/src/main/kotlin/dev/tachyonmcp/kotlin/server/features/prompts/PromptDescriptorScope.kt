// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.features.prompts

import dev.tachyonmcp.kotlin.server.TachyonDsl
import dev.tachyonmcp.kotlin.server.domain.PromptArgument
import dev.tachyonmcp.kotlin.server.domain.PromptArgumentBuilder
import dev.tachyonmcp.kotlin.server.json.toJacksonNode
import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.PromptArgument
import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode
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
        public var inputSchema: JsonNode? = null
        public var icons: List<Icon>? = null

        /** Sets the input schema from a kotlinx-serialization [JsonObject]. */
        public fun inputSchema(json: JsonObject) {
            inputSchema = json.toJacksonNode()
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
            return PromptDescriptor.of(
                n,
                description,
                title,
                arguments,
                inputSchema,
                icons,
            )
        }
    }

/** Builds a [PromptDescriptor] with a receiver DSL. */
@OptIn(ExperimentalContracts::class)
public inline fun PromptDescriptor(block: PromptDescriptorScope.() -> Unit): PromptDescriptor {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return PromptDescriptorScope().apply(block).build()
}
