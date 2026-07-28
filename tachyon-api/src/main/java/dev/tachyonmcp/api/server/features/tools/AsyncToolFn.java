/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tools;

import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.Args;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous tool function. Unlike {@link ToolFn}, {@link #apply} does not throw checked
 * exceptions — failures propagate through the returned {@link CompletionStage}, matching
 * {@code AsyncResourceHandler} and {@code AsyncPromptHandler}.
 *
 * <p>Receives the full {@link ToolRequest} — call {@link ToolRequest#arguments()} for parsed
 * {@link Args}, or read {@link ToolRequest#progressToken()}
 * or {@link ToolRequest#task()} directly when needed.
 *
 * @author Konstantin Pavlov
 */
@FunctionalInterface
public interface AsyncToolFn {

    /**
     * Executes the tool function asynchronously with the given request.
     *
     * @param ctx     the interaction context
     * @param request the tool request containing arguments and metadata
     * @return a future that completes with the tool result
     */
    CompletionStage<? extends ToolResult> apply(InteractionContext ctx, ToolRequest request);
}
