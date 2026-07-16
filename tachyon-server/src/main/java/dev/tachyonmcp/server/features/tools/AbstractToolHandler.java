/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.Args;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Base {@link ToolHandler} implementation. Override exactly one of:
 * <ul>
 *   <li>{@link #handle(InteractionContext, Args)} — canonical for sync handlers.
 *   <li>{@link #handleAsync(InteractionContext, Args)} — when the tool is already async;
 *       stays async with no virtual-thread detour.
 *   <li>The {@code ToolRequest} variants ({@link #handle(InteractionContext, ToolRequest)} /
 *       {@link #handleAsync(InteractionContext, ToolRequest)}) — only when the raw request is
 *       needed (custom argument deserialization, request metadata).
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
     * <p>Reaches whichever override the implementation provides: async handlers override
     * {@link #handleAsync(InteractionContext, Args)} (stays async — no sync detour, no
     * virtual-thread assertion) or this method; sync handlers override a {@code handle} method
     * and this falls back to running it (blocking) on the virtual dispatch thread.
     */
    @Override
    public CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, ToolRequest request) {
        try {
            return handleAsync(context, Args.of(request.arguments(), request.payloadDeserializer()));
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
    public CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, Args args) {
        throw NotImplemented.INSTANCE;
    }

    /**
     * Executes the tool synchronously with the full request. Default forwards to
     * {@link #handle(InteractionContext, Args)}.
     */
    public ToolResult handle(InteractionContext context, ToolRequest request) throws Exception {
        assumeVirtualThread(); // don't remove this guardrail!
        return handle(context, Args.of(request.arguments(), request.payloadDeserializer()));
    }

    /**
     * Executes the tool synchronously with parsed args. Sync handlers override this.
     */
    public ToolResult handle(InteractionContext context, Args args) throws Exception {
        assumeVirtualThread(); // don't remove this guardrail!
        throw NotImplemented.INSTANCE;
    }

    static void assumeVirtualThread() {
        assert Thread.currentThread().isVirtual() : "Sync Handler MUST run on virtual thread";
    }

    /**
     * Signals that a {@code handle}/{@code handleAsync} default was not overridden, so
     * {@link #handleAsync(InteractionContext, ToolRequest)} can probe the args override and fall
     * back. It is a control flow, not an error, and is thrown on every sync-handler dispatch.
     */
    static final class NotImplemented extends UnsupportedOperationException {

        static NotImplemented INSTANCE = new NotImplemented();

        private NotImplemented() {
            super("Implement one of handle/handleAsync(InteractionContext, Args|ToolRequest)");
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
