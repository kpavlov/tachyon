/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tools;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.runtime.InteractionContext;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous, typed tool function. Unlike {@link TypedToolFn}, {@link #apply} does not throw
 * checked exceptions — failures propagate through the returned {@link CompletionStage}, matching
 * {@link AsyncToolFn}.
 *
 * <p>Use this with {@link Tools#registerAsync(Class, Class, ToolDescriptor, AsyncTypedToolFn)}
 * when a handler only needs the decoded input, not {@link ToolRequest#progressToken()} — register
 * with the plain {@link AsyncToolFn} for that.
 *
 * @param <I> the decoded input type
 * @param <O> the result type, wrapped as structured content
 * @author Konstantin Pavlov
 */
@FunctionalInterface
@ExperimentalApi
public interface AsyncTypedToolFn<I, O> {

    /**
     * Invokes the handler with the decoded input.
     *
     * @param ctx the interaction context
     * @param input the tool arguments decoded into {@code I}
     * @return a future that completes with the result, wrapped as structured content; failures
     *     propagate through the returned stage
     */
    CompletionStage<? extends O> apply(InteractionContext ctx, I input);
}
