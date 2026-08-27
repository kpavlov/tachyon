/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tools;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.runtime.InteractionContext;

/**
 * Synchronous, typed tool function. Receives arguments already decoded into {@code I} instead of
 * the raw {@link ToolRequest}; the returned {@code O} is wrapped as structured content, matching
 * {@code ToolResult.structured(Object)}.
 *
 * <p>Use this with {@link Tools#register(Class, Class, ToolDescriptor, TypedToolFn)} when a
 * handler only needs the decoded input, not {@link ToolRequest#progressToken()} — register with
 * the plain {@link ToolFn} for that.
 *
 * @param <I> the decoded input type
 * @param <O> the result type, wrapped as structured content
 * @author Konstantin Pavlov
 */
@FunctionalInterface
@ExperimentalApi
public interface TypedToolFn<I, O> {

    /**
     * Invokes the handler with the decoded input.
     *
     * @param ctx the interaction context
     * @param input the tool arguments decoded into {@code I}
     * @return the result, wrapped as structured content
     * @throws Exception if the handler fails; the dispatcher logs it and maps it to a JSON-RPC
     *     error
     */
    O apply(InteractionContext ctx, I input) throws Exception;
}
