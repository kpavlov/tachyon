/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;

/**
 * Synchronous tool function. Unlike {@link java.util.function.BiFunction}, {@link #apply} may
 * throw checked exceptions — the dispatcher already logs them and maps them to a JSON-RPC error,
 * exactly as it does for {@link dev.tachyonmcp.server.features.resources.ResourceHandler} and
 * {@link dev.tachyonmcp.server.features.prompts.PromptHandler}.
 *
 * <p>Receives the full {@link ToolRequest} — call {@link ToolRequest#arguments()} for parsed
 * {@link dev.tachyonmcp.server.domain.Args}, or read {@link ToolRequest#progressToken()},
 * {@link ToolRequest#cancellation()}, or {@link ToolRequest#task()} directly when needed.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface ToolFn {

    ToolResult apply(InteractionContext ctx, ToolRequest request) throws Exception;
}
