// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.features.prompts

import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.PromptArgument
import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.json.toJacksonNode
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

        @PublishedApi
        internal fun build(): PromptDescriptor {
            val n = requireNotNull(name) { "PromptDescriptor.name is required" }
            return PromptDescriptor(
                name = n,
                description = description,
                title = title,
                arguments = arguments,
                inputSchema = inputSchema,
            )
        }
    }

/** Sets the input schema from a [JsonObject]. Requires kotlinx-serialization-json on the classpath. */
public fun PromptDescriptorScope.inputSchema(json: JsonObject) {
    inputSchema = json.toJacksonNode()
}

@OptIn(ExperimentalContracts::class)
public inline fun promptDescriptor(
    name: String,
    configure: PromptDescriptorScope.() -> Unit = {},
): PromptDescriptor {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    return PromptDescriptorScope()
        .apply {
            this.name = name
            configure()
        }.build()
}
