/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Handles tool execution. One handler per tool.
 *
 * <p>{@link #handle} runs on a virtual thread — blocking for I/O is the intended contract.
 * Never use {@code synchronized} or call native methods (pins the carrier thread).
 * Use {@link java.util.concurrent.locks.ReentrantLock} instead. For CPU-bound work or
 * third-party code that may synchronize, offload to
 * {@code context.server().executor()}.
 *
 * @author Konstantin Pavlov
 */
public interface ToolHandler {

    /**
     * Returns the metadata descriptor for this tool.
     */
    ToolDescriptor descriptor();

    /**
     * Executes the tool synchronously with parsed args. Sync handlers override this.
     */
    default ToolResult handle(InteractionContext context, ToolArgs args) throws Exception {
        assumeVirtualThread(); // don't remove this guardrail!
        throw NotImplemented.INSTANCE;
    }

    /**
     * Executes the tool synchronously with the full request. Default forwards to
     * {@link #handle(InteractionContext, ToolArgs)}.
     */
    default ToolResult handle(InteractionContext context, ToolRequest request) throws Exception {
        assumeVirtualThread(); // don't remove this guardrail!
        return handle(context, ToolArgs.of(request.arguments(), request.payloadDeserializer()));
    }

    /**
     * Executes the tool asynchronously with the full request — the single method the dispatcher
     * invokes.
     *
     * <p>Reaches whichever override the implementation provides: async handlers override
     * {@link #handleAsync(InteractionContext, ToolArgs)} (stays async — no sync detour, no
     * virtual-thread assertion) or this method; sync handlers override a {@code handle} method
     * and the default falls back to running it (blocking) on the virtual dispatch thread.
     */
    default CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, ToolRequest request) {
        try {
            return handleAsync(context, ToolArgs.of(request.arguments(), request.payloadDeserializer()));
        } catch (NotImplemented noAsyncArgs) {
            try {
                return CompletableFuture.completedStage(handle(context, request));
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }

    /**
     * Executes the tool asynchronously with parsed args. Async handlers override this. The default
     * signals "not implemented" so {@link #handleAsync(InteractionContext, ToolRequest)} can fall
     * back to the sync path.
     */
    default CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, ToolArgs args) {
        throw NotImplemented.INSTANCE;
    }

    static ToolHandler of(ToolDescriptor descriptor, BiFunction<InteractionContext, ToolArgs, ToolResult> fn) {
        return new AbstractToolHandler(descriptor) {

            @Override
            public ToolResult handle(InteractionContext ctx, ToolArgs args) {
                assumeVirtualThread(); // don't remove this guardrail!
                return fn.apply(ctx, args);
            }
        };
    }

    static ToolHandler ofRequest(
            ToolDescriptor descriptor, BiFunction<InteractionContext, ToolRequest, ToolResult> fn) {
        return new AbstractToolHandler(descriptor) {

            @Override
            public ToolResult handle(InteractionContext ctx, ToolRequest request) {
                assumeVirtualThread(); // don't remove this guardrail!
                return fn.apply(ctx, request);
            }
        };
    }

    static ToolHandler of(
            Consumer<ToolDescriptor.Builder> configurer, BiFunction<InteractionContext, ToolArgs, ToolResult> fn) {
        return new AbstractToolHandler(configurer) {

            @Override
            public ToolResult handle(InteractionContext ctx, ToolArgs args) {
                assumeVirtualThread(); // don't remove this guardrail!
                return fn.apply(ctx, args);
            }
        };
    }

    /**
     * Creates a simple sync ToolHandler from a name, description, and function.
     */
    static ToolHandler of(
            String name, @Nullable String description, BiFunction<InteractionContext, ToolArgs, ToolResult> fn) {
        return of(builder -> builder.name(name).description(description), fn);
    }

    /**
     * Creates a simple sync ToolHandler from a name and function.
     */
    static ToolHandler of(String name, BiFunction<InteractionContext, ToolArgs, ToolResult> fn) {
        return of(builder -> builder.name(name), fn);
    }

    static ToolHandler ofAsync(
            ToolDescriptor descriptor, BiFunction<InteractionContext, ToolArgs, CompletionStage<ToolResult>> fn) {
        return new AbstractToolHandler(descriptor) {

            @Override
            public CompletionStage<? extends ToolResult> handleAsync(InteractionContext ctx, ToolArgs args) {
                return fn.apply(ctx, args);
            }
        };
    }

    static ToolHandler ofAsyncRequest(
            ToolDescriptor descriptor, BiFunction<InteractionContext, ToolRequest, CompletionStage<ToolResult>> fn) {
        return new AbstractToolHandler(descriptor) {

            @Override
            public CompletionStage<? extends ToolResult> handleAsync(InteractionContext ctx, ToolRequest request) {
                return fn.apply(ctx, request);
            }
        };
    }

    static ToolHandler ofAsync(
            Consumer<ToolDescriptor.Builder> configurer,
            BiFunction<InteractionContext, ToolArgs, CompletionStage<ToolResult>> fn) {
        return new AbstractToolHandler(configurer) {

            @Override
            public CompletionStage<? extends ToolResult> handleAsync(InteractionContext ctx, ToolArgs args) {
                return fn.apply(ctx, args);
            }
        };
    }

    /**
     * Creates a simple async ToolHandler from a name, description, and function.
     */
    static ToolHandler ofAsync(
            String name,
            @Nullable String description,
            BiFunction<InteractionContext, ToolArgs, CompletionStage<ToolResult>> fn) {
        return ofAsync(builder -> builder.name(name).description(description), fn);
    }

    /**
     * Creates a simple async ToolHandler from a name and function.
     */
    static ToolHandler ofAsync(String name, BiFunction<InteractionContext, ToolArgs, CompletionStage<ToolResult>> fn) {
        return ofAsync(builder -> builder.name(name), fn);
    }

    private static void assumeVirtualThread() {
        assert Thread.currentThread().isVirtual() : "Sync Handler MUST run on virtual thread";
    }

    /**
     * Signals that a {@code handle}/{@code handleAsync} default was not overridden, so
     * {@link #handleAsync(InteractionContext, ToolRequest)} can probe the args override and fall
     * back. It is a control flow, not an error, and is thrown on every sync-handler dispatch.
     */
    final class NotImplemented extends UnsupportedOperationException {

        static NotImplemented INSTANCE = new NotImplemented();

        private NotImplemented() {
            super("Implement one of handle/handleAsync(InteractionContext, ToolArgs|ToolRequest)");
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
