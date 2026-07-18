/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.Args;

/**
 * Synchronous tool function operating on the raw {@link ToolRequest} — use when the handler needs
 * the progress token, cancellation, or task handle instead of just parsed {@link Args}. See
 * {@link ToolFn}.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface ToolRequestFn {

    ToolResult apply(InteractionContext ctx, ToolRequest request) throws Exception;
}
