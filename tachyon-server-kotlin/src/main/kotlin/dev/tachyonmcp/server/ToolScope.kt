// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.features.tools.ToolArgs
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import tools.jackson.databind.JsonNode

@TachyonDsl
public class ToolScope
    @PublishedApi
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
                .builder(name)
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
                .builder(name)
                .description(description)
                .schemas(inputSchema, outputSchema, toolName = name)
                .build(),
            handler,
        ),
    )
