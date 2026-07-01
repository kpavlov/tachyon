// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.domain.PromptArgument
import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import tools.jackson.databind.JsonNode

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
            return PromptDescriptor.of(n, description, title, arguments, inputSchema)
        }
    }

public fun promptDescriptor(
    name: String,
    configure: PromptDescriptorScope.() -> Unit = {},
): PromptDescriptor =
    PromptDescriptorScope()
        .apply {
            this.name = name
            configure()
        }.build()
