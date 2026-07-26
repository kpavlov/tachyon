/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.tools;

import static dev.tachyonmcp.server.features.HandlerFutures.assumeVirtualThread;

import dev.tachyonmcp.runtime.InteractionContext;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Base {@link ToolHandler} implementation. Override exactly one method:
 * <ul>
 *   <li>{@link #handle(InteractionContext, ToolRequest)} for synchronous handlers.
 *   <li>{@link #handleAsync(InteractionContext, ToolRequest)} for asynchronous handlers.
 * </ul>
 *
 * @author Konstantin Pavlov
 */
public abstract class AbstractToolHandler implements ToolHandler {

    private final ToolDescriptor descriptor;

    public AbstractToolHandler(ToolDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "ToolDescriptor must not be null");
        this.descriptor = descriptor;
    }

    public AbstractToolHandler(Consumer<ToolDescriptor.Builder> descriptorConfigurer) {
        this(configure(descriptorConfigurer));
    }

    private static ToolDescriptor configure(Consumer<ToolDescriptor.Builder> descriptorConfigurer) {
        final var builder = ToolDescriptor.builder();
        descriptorConfigurer.accept(builder);
        return builder.build();
    }

    public AbstractToolHandler(String name) {
        this(ToolDescriptor.builder().name(name).build());
    }

    @Override
    public ToolDescriptor descriptor() {
        return descriptor;
    }

    /**
     * Executes the tool asynchronously with the full request — the single method the dispatcher
     * invokes.
     *
     * <p>Async handlers override this method directly. Sync handlers override {@link
     * #handle(InteractionContext, ToolRequest)} and this method forwards to it.
     */
    @Override
    public CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, ToolRequest request) {
        try {
            return CompletableFuture.completedStage(handle(context, request));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Executes the tool synchronously with the full request. Sync handlers override this method.
     */
    public ToolResult handle(InteractionContext context, ToolRequest request) throws Exception {
        assumeVirtualThread(); // don't remove this guardrail!
        throw new UnsupportedOperationException("Override handle or handleAsync");
    }
}
