/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import static dev.tachyonmcp.server.features.HandlerFutures.assumeVirtualThread;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.ServerFeature;
import dev.tachyonmcp.server.domain.Args;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Handles tool execution. One handler per tool.
 *
 * <p>Override exactly one method in {@link AbstractToolHandler}:
 * <ul>
 *   <li>{@link AbstractToolHandler#handle(InteractionContext, Args)} — canonical for sync
 *       handlers.
 *   <li>{@link AbstractToolHandler#handleAsync(InteractionContext, Args)} — when the tool is
 *       already async; stays async with no virtual-thread detour.
 *   <li>The {@code ToolRequest} variants ({@link AbstractToolHandler#handle(InteractionContext,
 *       ToolRequest)} / {@link AbstractToolHandler#handleAsync(InteractionContext, ToolRequest)})
 *       — only when the raw request is needed (progress token, cancellation, task handle).
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
     */
    CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, ToolRequest request);

    static ToolHandler of(ToolDescriptor descriptor, ToolFn fn) {
        return new AbstractToolHandler(descriptor) {

            @Override
            public ToolResult handle(InteractionContext ctx, Args args) throws Exception {
                assumeVirtualThread(); // don't remove this guardrail!
                return fn.apply(ctx, args);
            }
        };
    }

    static ToolHandler ofRequest(ToolDescriptor descriptor, ToolRequestFn fn) {
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
            public ToolResult handle(InteractionContext ctx, Args args) throws Exception {
                assumeVirtualThread(); // don't remove this guardrail!
                return fn.apply(ctx, args);
            }
        };
    }

    /**
     * Creates a simple sync ToolHandler from a name, description, and function.
     */
    static ToolHandler of(String name, @Nullable String description, ToolFn fn) {
        return of(builder -> builder.name(name).description(description), fn);
    }

    /**
     * Creates a simple sync ToolHandler from a name and function.
     */
    static ToolHandler of(String name, ToolFn fn) {
        return of(builder -> builder.name(name), fn);
    }

    static ToolHandler ofAsync(ToolDescriptor descriptor, AsyncToolFn fn) {
        return new AbstractToolHandler(descriptor) {

            @Override
            public CompletionStage<? extends ToolResult> handleAsync(InteractionContext ctx, Args args) {
                return fn.apply(ctx, args);
            }
        };
    }

    static ToolHandler ofAsyncRequest(ToolDescriptor descriptor, AsyncToolRequestFn fn) {
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
            public CompletionStage<? extends ToolResult> handleAsync(InteractionContext ctx, Args args) {
                return fn.apply(ctx, args);
            }
        };
    }

    /**
     * Creates a simple async ToolHandler from a name, description, and function.
     */
    static ToolHandler ofAsync(String name, @Nullable String description, AsyncToolFn fn) {
        return ofAsync(builder -> builder.name(name).description(description), fn);
    }

    /**
     * Creates a simple async ToolHandler from a name and function.
     */
    static ToolHandler ofAsync(String name, AsyncToolFn fn) {
        return ofAsync(builder -> builder.name(name), fn);
    }
}
