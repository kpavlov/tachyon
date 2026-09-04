/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.server.domain.RequestId;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.interceptor.McpOutcome;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a handler result or a thrown exception into the {@link McpOutcome} interceptors observe,
 * resolving everything protocol-specific through the negotiated {@code ProtocolResponseMapper}.
 *
 * <p>Single home for the classification so the intercepted and non-intercepted dispatch paths
 * cannot drift apart, and so nothing outside {@code tachyon-core} ever re-derives a JSON-RPC error
 * code — the two MCP versions map several {@link ServerError.Kind}s differently.
 */
@InternalApi
final class McpOutcomes {

    private static final Logger logger = LoggerFactory.getLogger(McpOutcomes.class);

    private McpOutcomes() {}

    /** Classifies a completed handler result. Errors are values here, not only failures. */
    static McpOutcome of(@Nullable Object result, DispatchContext context) {
        if (result instanceof ServerError error) {
            return failure(error, context);
        }
        if (context.responseMapper().isPayloadError(result)) {
            return new McpOutcome.PayloadFailure(result);
        }
        return new McpOutcome.Success(result);
    }

    /** Resolves {@code error} to the wire code this protocol version uses. */
    static McpOutcome.Failure failure(ServerError error, DispatchContext context) {
        return failure(error, context, null);
    }

    private static McpOutcome.Failure failure(ServerError error, DispatchContext context, @Nullable Throwable cause) {
        return new McpOutcome.Failure(
                error, context.responseMapper().error(error).code(), cause);
    }

    /**
     * Classifies a throwable from a handler or an interceptor. The cause rides along on the outcome
     * so an outer interceptor can trace or audit it; only the sanitized {@link ServerError} reaches
     * the client.
     */
    static McpOutcome.Failure failure(String method, Throwable throwable, DispatchContext context) {
        return failure(classify(context.requestId(), method, throwable), context, throwable);
    }

    /** Maps a thrown/failed handler or interceptor into the protocol-neutral error the response will carry. */
    static ServerError classify(@Nullable RequestId id, String method, Throwable ex) {
        var unwrapped = ex instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : ex;
        if (unwrapped instanceof CancellationException) {
            logger.debug("Handler cancelled: method={}, id={}", method, id);
            return ServerErrors.internalError("Internal error");
        }
        if (unwrapped instanceof RequestMappingException rme) {
            logger.debug("Request mapping failed: method={}, id={}: {}", method, id, rme.getMessage());
            return rme.error();
        }
        logger.warn("Handler exception: method={}, id={}: {}", method, id, unwrapped.getMessage(), unwrapped);
        return ServerErrors.internalError("Internal error");
    }
}
