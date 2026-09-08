// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
@file:JvmSynthetic

package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.api.runtime.InteractionContext
import dev.tachyonmcp.api.server.features.completions.CompletionRequest
import dev.tachyonmcp.kotlin.server.TachyonDsl

@TachyonDsl
public class CompletionScope
    internal constructor(
        public val ctx: InteractionContext,
        public val request: CompletionRequest,
    ) {
        /** Name of the argument being completed. */
        public val argumentName: String
            get() = request.argumentName()

        /** Current (partial) value typed for the argument. */
        public val argumentValue: String
            get() = request.argumentValue()

        /** Previously-resolved argument name/value pairs, or empty if none. */
        public val resolvedArguments: Map<String, String>
            get() = request.resolvedArguments()
    }
