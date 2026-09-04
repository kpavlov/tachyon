/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.interceptor;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.server.domain.ServerError;
import org.jspecify.annotations.Nullable;

/**
 * What one MCP operation produced, already resolved against the negotiated protocol version.
 *
 * <p>An {@link McpInterceptor} observes this instead of the raw handler result, because the fact it
 * most needs — the JSON-RPC error code the response will carry — is decided by the protocol codec,
 * which runs after the handler. Two MCP versions encode the same {@link ServerError.Kind}
 * differently, so any code that re-derived it would be wrong on one of them.
 *
 * <p>It is also the <em>only</em> channel a failure travels on: a throwing handler and a throwing
 * downstream interceptor both arrive as {@link Failure}, never as an exception out of
 * {@link McpInterceptor.Chain#proceed()}.
 *
 * @author Konstantin Pavlov
 */
@ExperimentalApi
public sealed interface McpOutcome {

    /**
     * The handler produced a result and the JSON-RPC response carries it.
     *
     * @param result the handler's result, already mapped to its wire shape; {@code null} for
     *               notifications, which have no response
     */
    record Success(@Nullable Object result) implements McpOutcome {}

    /**
     * The JSON-RPC call succeeded, but the result payload itself reports a failure — today only a
     * {@code tools/call} whose {@code CallToolResult} carries {@code isError: true}.
     *
     * <p>Still a success on the wire: the client receives a {@code result}, not an {@code error}.
     * The distinction exists for observability and policy, which need to tell "the tool ran and
     * said no" apart from "the tool ran and said yes".
     *
     * @param result the handler's result, already mapped to its wire shape
     */
    record PayloadFailure(@Nullable Object result) implements McpOutcome {}

    /**
     * The operation failed and the response is a JSON-RPC error envelope.
     *
     * <p>Prefer {@link McpInterceptor.Chain#reject(ServerError)} over constructing this directly —
     * it resolves {@code jsonRpcCode} for the protocol version in play, which is not something
     * calling code should have to know.
     *
     * @param error       the protocol-neutral error
     * @param jsonRpcCode the code this protocol version puts on the wire for {@code error}
     * @param cause       the exception this failure came from, or {@code null} when the handler
     *                    returned the error or an interceptor
     *                    {@link McpInterceptor.Chain#reject(ServerError) rejected}. Kept so folding
     *                    exceptions into outcomes does not lose the stack trace an audit log or
     *                    tracer needs; only {@code error} reaches the client.
     */
    record Failure(
            ServerError error, int jsonRpcCode, @Nullable Throwable cause) implements McpOutcome {}
}
