// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
@file:JvmSynthetic

package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.api.runtime.InteractionContext
import dev.tachyonmcp.api.server.features.tools.ToolRequest
import dev.tachyonmcp.api.server.features.tools.ToolResult
import dev.tachyonmcp.kotlin.server.TachyonDsl

@TachyonDsl
public class ToolScope
    internal constructor(
        public val ctx: InteractionContext,
        public val request: ToolRequest,
    ) {
        /**
         * Returns a [ToolResult] whose structured value is [value], serialized to
         * `structuredContent` by the serde configured in server config at encode time
         * (symmetric with [decode][dev.tachyonmcp.kotlin.server.domain.decode]).
         *
         * When [text] is omitted, no text block is attached and the server emits the
         * serialized JSON as the text content (MCP backwards-compat). Pass [text] to
         * supply a human-readable text block instead.
         *
         * For a pre-serialized JSON payload that skips the configured serde, use
         * [dev.tachyonmcp.api.server.features.tools.ToolResult.raw] directly.
         */
        public fun <T : Any> success(
            value: T,
            text: String? = null,
        ): ToolResult =
            if (text !=
                null
            ) {
                ToolResult.structured(value, text)
            } else {
                ToolResult.structured(value)
            }

        /** Returns a [ToolResult] carrying a single plain-text content block. */
        public fun text(text: String): ToolResult = ToolResult.text(text)

        /**
         * Returns a [ToolResult] built from the content blocks collected in [block]:
         *
         * ```kotlin
         * content {
         *     text("Answer")
         *     image(data, "image/png")
         * }
         * ```
         */
        public fun content(block: ContentScope.() -> Unit): ToolResult {
            val scope = ContentScope().apply(block)
            return ToolResult.content(*scope.blocks.toTypedArray())
        }
    }
