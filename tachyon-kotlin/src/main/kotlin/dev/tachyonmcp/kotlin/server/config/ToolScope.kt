// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
@file:JvmSynthetic

package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.kotlin.server.TachyonDsl
import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.features.tools.ToolRequest
import dev.tachyonmcp.server.features.tools.ToolResult

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
         * [ToolResult.raw] directly.
         */
        public fun <T : Any> success(
            value: T,
            text: String? = null,
        ): ToolResult = if (text != null) ToolResult.of(value, text) else ToolResult.of(value)

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
