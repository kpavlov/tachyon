// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.features.prompts

import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.PromptArgument
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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
            return PromptArgument(
                name = n,
                title = title,
                description = description,
                required = required,
            )
        }
    }

public fun promptArgument(
    name: String,
    description: String? = null,
    required: Boolean? = null,
): PromptArgument = PromptArgument(name = name, description = description, required = required)

@OptIn(ExperimentalContracts::class)
public inline fun promptArgument(configure: PromptArgumentScope.() -> Unit): PromptArgument {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    return PromptArgumentScope()
        .apply {
            configure()
        }.build()
}
