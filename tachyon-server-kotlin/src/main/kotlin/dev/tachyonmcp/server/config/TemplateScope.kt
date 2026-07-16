// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.UriTemplateValue

@TachyonDsl
public class TemplateScope
    internal constructor(
        public val ctx: InteractionContext,
        public val uri: String,
        public val params: Map<String, UriTemplateValue>,
    ) {
        public fun param(name: String): String =
            when (val value = value(name)) {
                is UriTemplateValue.Scalar -> value.value()
                is UriTemplateValue.Sequence ->
                    throw IllegalArgumentException("template variable is not scalar: $name")
            }

        public fun sequence(name: String): List<String> =
            when (val value = value(name)) {
                is UriTemplateValue.Scalar ->
                    throw IllegalArgumentException("template variable is not a sequence: $name")
                is UriTemplateValue.Sequence -> value.values()
            }

        private fun value(name: String): UriTemplateValue =
            params[name] ?: throw IllegalArgumentException("missing template variable: $name")
    }
