// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.ServerBuilder
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.features.tools.ToolArgs
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.features.tools.asyncHandler
import dev.tachyonmcp.server.json.RawJson
import dev.tachyonmcp.server.json.schemas
import kotlinx.serialization.json.Json
import tools.jackson.databind.JsonNode

@TachyonDsl
public class ToolScope
    internal constructor(
        public val ctx: InteractionContext,
        public val args: ToolArgs,
    )

public fun ServerBuilder.tool(
    name: String,
    description: String? = null,
    inputSchema: JsonNode? = null,
    outputSchema: JsonNode? = null,
    handler: suspend ToolScope.() -> ToolResult,
): ServerBuilder =
    tool(
        asyncHandler(
            ToolDescriptor
                .builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .outputSchema(outputSchema)
                .build(),
            handler,
        ),
    )

public fun ServerBuilder.tool(
    name: String,
    description: String? = null,
    inputSchema: String,
    outputSchema: String? = null,
    handler: suspend ToolScope.() -> ToolResult,
): ServerBuilder =
    tool(
        asyncHandler(
            ToolDescriptor
                .builder()
                .name(name)
                .description(description)
                .schemas(inputSchema, outputSchema, toolName = name)
                .build(),
            handler,
        ),
    )

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
