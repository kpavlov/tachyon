/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous tool function operating on the raw {@link ToolRequest}. See {@link AsyncToolFn}
 * and {@link ToolRequestFn}.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface AsyncToolRequestFn {

    CompletionStage<? extends ToolResult> apply(InteractionContext ctx, ToolRequest request);
}
