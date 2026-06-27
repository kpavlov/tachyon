/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.session.McpContext;
import java.util.concurrent.CompletionStage;

public interface ToolHandler<R extends ToolResult> {

    ToolDescriptor descriptor();

    CompletionStage<R> handle(ToolRequest request, McpContext context) throws Exception;
}
