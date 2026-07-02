/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import java.util.concurrent.CompletionStage;

/** Handles tool execution. One handler per tool. */
public interface ToolHandler {

    /** Returns the metadata descriptor for this tool. */
    ToolDescriptor descriptor();

    /** Executes the tool with the given context and request. */
    CompletionStage<? extends ToolResult> handle(InteractionContext context, ToolRequest request);
}
