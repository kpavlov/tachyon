/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.session.McpContext;
import java.util.concurrent.CompletionStage;

/** Handles tool execution. One handler per tool. */
public interface ToolHandler {

    /** Returns the metadata descriptor for this tool. */
    ToolDescriptor descriptor();

    /** Executes the tool with the given request and context. */
    CompletionStage<? extends ToolResult> handle(ToolRequest request, McpContext context);
}
