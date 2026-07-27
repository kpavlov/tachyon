/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.tools;

import static dev.tachyonmcp.server.features.HandlerFutures.assumeVirtualThread;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.ServerFeature;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Handles tool execution. One handler per tool.
 *
 * <p>Override exactly one method in {@link AbstractToolHandler}:
 * <ul>
 *   <li>{@link AbstractToolHandler#handle(InteractionContext, ToolRequest)} for synchronous
 *       handlers.
 *   <li>{@link AbstractToolHandler#handleAsync(InteractionContext, ToolRequest)} for asynchronous
 *       handlers.
 * </ul>
 *
 * @author Konstantin Pavlov
 */
public interface ToolHandler extends ServerFeature<ToolDescriptor> {

    /**
     * Returns the metadata descriptor for this tool.
     */
    ToolDescriptor descriptor();

    /**
     * Executes the tool asynchronously with the full request — the single method the dispatcher
     * invokes. Override in {@link AbstractToolHandler} or implement directly.
     *
     * @param context the interaction context
     * @param request the tool invocation request
     * @return a completion stage yielding the tool result
     */
    CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, ToolRequest request);

    static ToolHandler of(ToolDescriptor descriptor, ToolFn fn) {
        return new AbstractToolHandler(descriptor) {

            @Override
            public ToolResult handle(InteractionContext ctx, ToolRequest request) throws Exception {
                assumeVirtualThread(); // don't remove this guardrail!
                return fn.apply(ctx, request);
            }
        };
    }

    static ToolHandler of(Consumer<ToolDescriptor.Builder> configurer, ToolFn fn) {
        return new AbstractToolHandler(configurer) {

            @Override
            public ToolResult handle(InteractionContext ctx, ToolRequest request) throws Exception {
                assumeVirtualThread(); // don't remove this guardrail!
                return fn.apply(ctx, request);
            }
        };
    }

    /**
     * Creates a simple sync ToolHandler from a name, description, and function.
     *
     * @param name        the tool name
     * @param description the tool description, or {@code null}
     * @param fn          the tool function
     * @return a new handler
     */
    static ToolHandler of(String name, @Nullable String description, ToolFn fn) {
        return of(builder -> builder.name(name).description(description), fn);
    }

    /**
     * Creates a simple sync ToolHandler from a name and function.
     *
     * @param name the tool name
     * @param fn   the tool function
     * @return a new handler
     */
    static ToolHandler of(String name, ToolFn fn) {
        return of(builder -> builder.name(name), fn);
    }

    static ToolHandler ofAsync(ToolDescriptor descriptor, AsyncToolFn fn) {
        return new AbstractToolHandler(descriptor) {

            @Override
            public CompletionStage<? extends ToolResult> handleAsync(InteractionContext ctx, ToolRequest request) {
                return fn.apply(ctx, request);
            }
        };
    }

    static ToolHandler ofAsync(Consumer<ToolDescriptor.Builder> configurer, AsyncToolFn fn) {
        return new AbstractToolHandler(configurer) {

            @Override
            public CompletionStage<? extends ToolResult> handleAsync(InteractionContext ctx, ToolRequest request) {
                return fn.apply(ctx, request);
            }
        };
    }

    /**
     * Creates a simple async ToolHandler from a name, description, and function.
     *
     * @param name        the tool name
     * @param description the tool description, or {@code null}
     * @param fn          the async tool function
     * @return a new handler
     */
    static ToolHandler ofAsync(String name, @Nullable String description, AsyncToolFn fn) {
        return ofAsync(builder -> builder.name(name).description(description), fn);
    }

    /**
     * Creates a simple async ToolHandler from a name and function.
     *
     * @param name the tool name
     * @param fn   the async tool function
     * @return a new handler
     */
    static ToolHandler ofAsync(String name, AsyncToolFn fn) {
        return ofAsync(builder -> builder.name(name), fn);
    }
}
