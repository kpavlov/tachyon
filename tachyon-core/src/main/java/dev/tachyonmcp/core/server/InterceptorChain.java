/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.interceptor.McpInterceptor;
import dev.tachyonmcp.api.server.interceptor.McpInvocation;
import dev.tachyonmcp.api.server.interceptor.McpOutcome;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Immutable node in the {@link McpInterceptor} chain: each {@link #proceed()} builds the node for
 * the next interceptor rather than advancing a shared cursor, so an interceptor may call {@code
 * proceed()} more than once (retry) and two threads never race on the position.
 *
 * <p>Runs entirely on the dispatch virtual thread, so chain and handler share one stack — which is
 * what keeps the dispatch's thread-scoped outbound-stream binding
 * ({@link OutboundSseStreamMessageRouter}) in scope for the handler.
 */
@InternalApi
final class InterceptorChain implements McpInterceptor.Chain {

    private final List<McpInterceptor> interceptors;
    private final int index;
    private final McpInvocation invocation;
    private final DispatchContext context;
    private final Supplier<McpOutcome> terminal;

    private InterceptorChain(
            List<McpInterceptor> interceptors,
            int index,
            McpInvocation invocation,
            DispatchContext context,
            Supplier<McpOutcome> terminal) {
        this.interceptors = interceptors;
        this.index = index;
        this.invocation = invocation;
        this.context = context;
        this.terminal = terminal;
    }

    /**
     * Runs {@code invocation} through every interceptor, outermost first, ending in {@code
     * terminal}. Callers must check for an empty {@code interceptors} list first — the
     * zero-interceptor path is meant to allocate nothing.
     */
    static McpOutcome run(
            List<McpInterceptor> interceptors,
            McpInvocation invocation,
            DispatchContext context,
            Supplier<McpOutcome> terminal) {
        return new InterceptorChain(interceptors, 0, invocation, context, terminal).proceed();
    }

    @Override
    public McpOutcome proceed() {
        // Failures are values on this seam: everything downstream -- the next interceptor or the
        // handler -- is classified here, so proceed() never throws on the remainder's behalf and an
        // outer interceptor observes the same Failure the wire will carry instead of having to catch.
        try {
            if (index == interceptors.size()) {
                return terminal.get();
            }
            final var interceptor = interceptors.get(index);
            final var next = new InterceptorChain(interceptors, index + 1, invocation, context, terminal);
            return Objects.requireNonNull(
                    interceptor.intercept(invocation, next),
                    () -> interceptor.getClass().getName() + ".intercept returned null");
        } catch (Exception e) {
            return McpOutcomes.failure(invocation.method(), e, context);
        }
    }

    @Override
    public McpOutcome reject(ServerError error) {
        Objects.requireNonNull(error, "error");
        return McpOutcomes.failure(error, context);
    }
}
