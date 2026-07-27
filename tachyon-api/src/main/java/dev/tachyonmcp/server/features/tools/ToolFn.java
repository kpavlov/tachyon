/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.Args;

/**
 * Synchronous tool function. Unlike {@link java.util.function.BiFunction}, {@link #apply} may
 * throw checked exceptions — the dispatcher already logs them and maps them to a JSON-RPC error,
 * exactly as it does for {@code ResourceHandler} and {@code PromptHandler}.
 *
 * <p>Receives the full {@link ToolRequest} — call {@link ToolRequest#arguments()} for parsed
 * {@link Args}, or read {@link ToolRequest#progressToken()}
 * or {@link ToolRequest#task()} directly when needed.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface ToolFn {

    ToolResult apply(InteractionContext ctx, ToolRequest request) throws Exception;
}
