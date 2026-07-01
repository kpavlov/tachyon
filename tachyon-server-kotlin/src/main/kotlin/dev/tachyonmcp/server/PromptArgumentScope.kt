// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.domain.PromptArgument

@TachyonDsl
public class PromptArgumentScope
    @PublishedApi
    internal constructor() {
        public var name: String? = null
        public var title: String? = null
        public var description: String? = null
        public var required: Boolean? = null

        @PublishedApi
        internal fun build(): PromptArgument {
            val n = requireNotNull(name) { "PromptArgument.name is required" }
            return PromptArgument.of(n, title, description, required)
        }
    }

public fun promptArgument(
    name: String,
    description: String? = null,
    required: Boolean? = null,
): PromptArgument = PromptArgument.of(name, null, description, required)

public fun promptArgument(configure: PromptArgumentScope.() -> Unit): PromptArgument =
    PromptArgumentScope()
        .apply {
            configure()
        }.build()
