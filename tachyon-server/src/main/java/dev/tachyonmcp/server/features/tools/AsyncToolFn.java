/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.Args;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous tool function. Unlike {@link ToolFn}, {@link #apply} does not throw checked
 * exceptions — failures propagate through the returned {@link CompletionStage}, matching
 * {@link dev.tachyonmcp.server.features.resources.AsyncResourceHandler} and
 * {@link dev.tachyonmcp.server.features.prompts.AsyncPromptHandler}.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface AsyncToolFn {

    CompletionStage<? extends ToolResult> apply(InteractionContext ctx, Args args);
}
