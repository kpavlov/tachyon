/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.Args;

/**
 * Synchronous tool function. Unlike {@link java.util.function.BiFunction}, {@link #apply} may
 * throw checked exceptions — the dispatcher already logs them and maps them to a JSON-RPC error,
 * exactly as it does for {@link dev.tachyonmcp.server.features.resources.ResourceHandler} and
 * {@link dev.tachyonmcp.server.features.prompts.PromptHandler}.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface ToolFn {

    ToolResult apply(InteractionContext ctx, Args args) throws Exception;
}
