/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.interceptor;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.server.domain.ServerError;

/**
 * Around-advice over one inbound MCP request or notification — the server's single cross-cutting
 * seam, where tracing, auditing, authorization and rate limiting belong. Register with {@code
 * ServerBuilder.withInterceptors(...)}; the first registered is the outermost.
 *
 * <p>{@link #intercept} runs on the dispatch <b>virtual thread</b> and blocks until the rest of the
 * chain is done, so an interceptor is ordinary sequential code:
 *
 * <pre>{@code
 * final class TimingInterceptor implements McpInterceptor {
 *     public McpOutcome intercept(McpInvocation invocation, Chain chain) {
 *         final var startNanos = System.nanoTime();
 *         try {
 *             return chain.proceed();
 *         } finally {
 *             record(invocation.method(), System.nanoTime() - startNanos);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>Failures are values, already resolved against the negotiated protocol version — so inspecting
 * what happened is one {@code switch} and no {@code catch}:
 *
 * <pre>{@code
 * switch (chain.proceed()) {
 *     case McpOutcome.Success s -> {}
 *     case McpOutcome.PayloadFailure p -> countToolError();
 *     case McpOutcome.Failure f -> count(f.jsonRpcCode(), f.error().kind());
 * }
 * }</pre>
 *
 * <h2>Contract</h2>
 *
 * <ul>
 *   <li>Covers every operation that reaches a handler, {@code initialize} and notifications
 *       included. Requests refused earlier — unknown method, unknown or missing session — never
 *       reach the chain.
 *   <li>One instance serves every concurrent operation: be thread-safe and keep per-request state
 *       in locals, never in fields nor in {@link McpInvocation#context()}, whose attribute space is
 *       shared by every request on the connection.
 *   <li>Do not retain the {@link McpInvocation} beyond the call; see its javadoc.
 *   <li>Blocking for I/O is intended, but nothing may pin the carrier thread — prefer
 *       {@link java.util.concurrent.locks.ReentrantLock} over {@code synchronized}. Chain and
 *       handler share one stack, so a pinning interceptor pins the handler too.
 *   <li>An exception thrown out of {@link #intercept} becomes a JSON-RPC internal error, exactly as
 *       a throwing handler does, and reaches an outer interceptor as
 *       {@link McpOutcome.Failure#cause()}. Cover it with {@code finally}, not {@code catch}.
 *   <li>{@link Chain#reject(ServerError)} short-circuits the handler — the authorization and
 *       rate-limiting path. {@link Chain#proceed()} may be called more than once (retry).
 * </ul>
 *
 * @author Konstantin Pavlov
 */
@ExperimentalApi
@FunctionalInterface
public interface McpInterceptor {

    /**
     * Wraps the dispatch of one MCP operation. Runs on the dispatch virtual thread and may block.
     *
     * @param invocation the operation being dispatched
     * @param chain      continuation to the next interceptor, or to the handler
     * @return the outcome, which an interceptor may substitute
     * @throws Exception any failure; mapped to a JSON-RPC internal error, as a throwing handler is
     */
    McpOutcome intercept(McpInvocation invocation, Chain chain) throws Exception;

    /**
     * Continuation handed to an {@link McpInterceptor}: the next interceptor in the chain, or the
     * method handler when this is the innermost one.
     *
     * <p><strong>Not for implementation outside Tachyon.</strong> Methods may be added in any
     * release, so implementing it in application code will break on upgrade. Test an interceptor
     * against a running server instead.
     */
    interface Chain {

        /**
         * Runs the remainder of the chain and the handler, blocking until they produce an outcome.
         *
         * <p>Never throws on their behalf: a downstream failure comes back as
         * {@link McpOutcome.Failure}, carrying the resolved wire code and the originating
         * {@link McpOutcome.Failure#cause() cause}.
         *
         * @return the outcome of the remainder of the dispatch
         */
        McpOutcome proceed();

        /**
         * Short-circuits the dispatch with a JSON-RPC error, without invoking the handler.
         *
         * <p>Resolves the wire code for the protocol version in play, so callers never hand-write
         * one — two MCP versions encode the same {@link ServerError.Kind} differently.
         *
         * @param error the error to answer with
         * @return the corresponding {@link McpOutcome.Failure}
         */
        McpOutcome reject(ServerError error);
    }
}
