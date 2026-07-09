// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.features.tools.ToolArgs
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.json.RawJson
import kotlinx.serialization.json.Json

@TachyonDsl
public class ToolScope
    internal constructor(
        public val ctx: InteractionContext,
        public val args: ToolArgs,
    )

/**
 * Returns a [ToolResult] whose structured value is [value], serialized to
 * `structuredContent` by the serde configured in server config at encode time
 * (symmetric with [decode][dev.tachyonmcp.server.features.tools.decode]).
 * Text content falls back to `value.toString()`; pass [text] to override.
 * Unlike [structured], honors a custom-configured serializer.
 */
public fun <T : Any> ToolScope.success(
    value: T,
    text: String = value.toString(),
): ToolResult = ToolResult.of(value, text)

/**
 * Produces a [ToolResult] with a structured value encoded via kotlinx-serialization.
 * The serialized JSON string serves as the text fallback.
 * The value must encode to a JSON object, as required by the MCP `structuredContent` field.
 * Uses [RawJson] to avoid a Jackson parse round-trip.
 * Requires kotlinx-serialization-json on the classpath.
 */
public inline fun <reified T> ToolScope.structured(
    value: T,
    json: Json = Json.Default,
): ToolResult {
    val text = json.encodeToString(value)
    // kotlinx.serialization never emits leading whitespace, so the first char decides the kind
    require(text.startsWith("{")) {
        "structuredContent must be a JSON object, got: $text"
    }
    return ToolResult.raw(text, text)
}
