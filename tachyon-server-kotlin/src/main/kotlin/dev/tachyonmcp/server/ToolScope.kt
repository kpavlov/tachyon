// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.features.tools.SyncToolHandler
import dev.tachyonmcp.server.features.tools.ToolArgs
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.session.McpContext
import tools.jackson.databind.JsonNode

@TachyonDsl
public class ToolScope
    @PublishedApi
    internal constructor(
        public val ctx: McpContext,
        public val args: ToolArgs,
    )

public fun ServerBuilder.tool(
    name: String,
    description: String? = null,
    inputSchema: JsonNode? = null,
    handler: ToolScope.() -> ToolResult<*>,
): ServerBuilder =
    tool(
        SyncToolHandler.of(
            name,
            description,
            inputSchema,
        ) { ctx, args -> ToolScope(ctx, args).handler() },
    )
