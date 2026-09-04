/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.runtime.AttributeKey;
import dev.tachyonmcp.api.server.domain.RequestId;
import dev.tachyonmcp.api.server.domain.ServerError;
import dev.tachyonmcp.api.server.features.HandlerFutures;
import dev.tachyonmcp.api.server.interceptor.McpInterceptor;
import dev.tachyonmcp.api.server.interceptor.McpOutcome;
import dev.tachyonmcp.api.server.session.SessionIdGenerator;
import dev.tachyonmcp.core.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.core.protocol.Protocols;
import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.runtime.ChannelContext;
import dev.tachyonmcp.core.runtime.Session;
import dev.tachyonmcp.core.runtime.SessionState;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.server.session.DefaultDispatchContext;
import dev.tachyonmcp.core.server.session.DispatchContext;
import dev.tachyonmcp.core.server.session.SessionEvent;
import dev.tachyonmcp.core.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.core.transport.jsonrpc.JsonRpcError;
import dev.tachyonmcp.core.transport.jsonrpc.JsonRpcMessage;
import dev.tachyonmcp.core.transport.netty.McpInitializationHandler;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the MCP server's per-request flow: parses JSON-RPC messages, establishes the session on
 * {@code initialize}, routes to registered handlers (including extension methods), tracks pending
 * requests, and encodes responses. Collaborator of {@link DefaultTachyonServer} — server holds state/registries,
 * this drives one request at a time.
 *
 * <p>MCP- and spec-version-specific: it special-cases {@code initialize}/task-status and binds the
 * {@code v2025_11_25} models/codecs. The version-specific call-sites (marked below) move behind an
 * {@code McpDialect} when a second spec version is wired.
 */
@InternalApi
public class McpDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(McpDispatcher.class);

    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_PING = "ping";

    /**
     * Interaction-context attribute key under which {@link McpInitializationHandler} stashes a
     * detached copy of the {@code initialize} HTTP request, so a custom
     * {@link SessionIdGenerator} can read its headers/URI.
     */
    public static final AttributeKey<HttpRequest> ATTR_INIT_REQUEST = AttributeKey.of("init.request");

    /**
     * Placeholder request for programmatic dispatch with no channel (the default generator ignores it).
     */
    private static final HttpRequest EMPTY_INIT_REQUEST =
            new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");

    public static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    private static final String NOTIFICATIONS_CANCELLED = "notifications/cancelled";

    private final Executor executor;

    private final ServerEngine server;

    public McpDispatcher(ServerEngine server, Executor executor) {
        this.server = server;
        this.executor = executor;
    }

    /**
     * Decorates the per-channel context with the per-request MCP dispatch surface. Without a channel
     * context (direct invocation, tests), fresh channel state is created for the default protocol.
     */
    private DispatchContext dispatchContext(@Nullable ChannelContext channelContext) {
        return dispatchContext(channelContext, null);
    }

    private DispatchContext dispatchContext(@Nullable ChannelContext channelContext, @Nullable RequestId id) {
        var channel = channelContext != null
                ? channelContext
                : Protocols.list().getFirst().createInteractionContext();
        return new DefaultDispatchContext(channel, server, id);
    }

    public sealed interface DispatchResult
            permits DispatchResult.Accepted, DispatchResult.Response, DispatchResult.Status {

        record Accepted() implements DispatchResult {
            static final Accepted INSTANCE = new Accepted();
        }

        /**
         * A dispatched JSON-RPC response (result or error envelope) ready to write to the wire.
         *
         * @param responseBody the encoded JSON-RPC response body
         * @param sessionId    the session id to echo back, if any
         * @param httpStatus   the real HTTP response status; JSON-RPC errors default to {@code 200}
         *                     per the JSON-RPC-over-HTTP convention (see {@link
         *                     JsonRpcError}) — some protocol-level
         *                     errors override it (e.g. {@code 400}/{@code 404})
         */
        record Response(byte[] responseBody, @Nullable String sessionId, int httpStatus) implements DispatchResult {
            public String responseBodyString() {
                return new String(responseBody, StandardCharsets.UTF_8);
            }
        }

        /**
         * Transport-level signal: the transport must reply with a raw HTTP {@code code}/{@code message},
         * not a JSON-RPC error envelope. Used for conditions the MCP Streamable HTTP spec ties to a
         * specific HTTP status rather than a JSON-RPC error code — e.g. a missing {@code MCP-Session-Id}
         * header (400) or an unknown/expired session (404).
         *
         * @param code    the HTTP status code
         * @param message the HTTP status message
         */
        record Status(int code, String message) implements DispatchResult {}
    }

    @Nullable
    public JsonRpcMessage parseMessage(ByteBuf body) {
        try {
            return JsonRpcCodec.parseRequest(body);
        } catch (Exception e) {
            logger.debug("Failed to parse JSON-RPC message", e);
            return null;
        }
    }

    public byte[] parseError(@Nullable ChannelContext channelContext) {
        var mapper = channelContext != null
                ? channelContext.protocol().responseMapper()
                : dispatchContext(null).responseMapper();
        return encodeError(null, ServerErrors.parseError(), mapper);
    }

    public CompletableFuture<DispatchResult> dispatchRequestAsync(
            RequestId id, String method, Object params, @Nullable String sessionId) {
        return dispatchRequestAsync(id, method, params, sessionId, null, null);
    }

    public CompletableFuture<DispatchResult> dispatchRequestAsync(
            RequestId id,
            String method,
            Object params,
            @Nullable String sessionId,
            @Nullable OutboundSseStream outboundSseStream,
            @Nullable ChannelContext channelContext) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(method, "method");

        var requestCtx = dispatchContext(channelContext, id);
        try {
            requestCtx.setPermittedLogLevel(requestCtx.requestMapper().permittedLogLevel(params));
        } catch (RequestMappingException e) {
            return CompletableFuture.completedFuture(errorResult(id, e.error(), requestCtx));
        }
        if (METHOD_INITIALIZE.equals(method)) {
            if (sessionId == null) {
                return dispatchInitializeAsync(id, params, requestCtx, channelContext);
            }
            return CompletableFuture.completedFuture(
                    errorResult(id, ServerErrors.invalidRequest("Session already initialized"), requestCtx));
        }

        if (server.isStateless()
                || !requestCtx.protocol().supportsSessions()
                || (sessionId == null && METHOD_PING.equals(method))) {

            requestCtx.setOutboundStream(outboundSseStream);
            var handler = lookupHandler(method, params, requestCtx);
            if (handler == null) {
                return CompletableFuture.completedFuture(
                        errorResult(id, ServerErrors.methodNotFound("Method not found"), requestCtx));
            }
            return invokeHandlerAsync(id, method, params, outboundSseStream, requestCtx, null, handler);
        }

        if (sessionId == null) {
            return CompletableFuture.completedFuture(new DispatchResult.Status(400, "Missing MCP-Session-Id header"));
        }

        var sessionOpt = server.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            return CompletableFuture.completedFuture(new DispatchResult.Status(404, "Unknown session"));
        }
        var session = sessionOpt.get();
        session.touch();

        requestCtx.setSession(session);
        requestCtx.setOutboundStream(outboundSseStream);

        var sessionState = session.state();
        if (sessionState == SessionState.CLOSED) {
            return CompletableFuture.completedFuture(
                    errorResult(id, ServerErrors.invalidRequest("Session is closed"), requestCtx));
        }
        if (sessionState == SessionState.INITIALIZING && !METHOD_PING.equals(method)) {
            return CompletableFuture.completedFuture(errorResult(
                    id, ServerErrors.invalidRequest("Session is not yet active, only ping allowed"), requestCtx));
        }

        var handler = lookupHandler(method, params, requestCtx);
        if (handler == null) {
            return CompletableFuture.completedFuture(
                    errorResult(id, ServerErrors.methodNotFound("Method not found"), requestCtx));
        }

        return invokeHandlerAsync(id, method, params, outboundSseStream, requestCtx, session, handler);
    }

    private @Nullable RpcMethodHandler<?, ?> lookupHandler(String method, Object params, DispatchContext ic) {
        var owningExtensionId = server.extensionForMethod(method);
        if (owningExtensionId != null) {
            if (!ic.isExtensionEnabled(owningExtensionId)) return null;
            if (server.extensionRequiresMeta(owningExtensionId)
                    && !ic.requestMapper().hasMetaKey(params, owningExtensionId)) return null;
        }
        return server.getHandler(method);
    }

    private <I, O> CompletableFuture<DispatchResult> invokeHandlerAsync(
            RequestId id,
            String method,
            Object rawParams,
            @Nullable OutboundSseStream outboundSseStream,
            DispatchContext context,
            @Nullable Session session,
            RpcMethodHandler<I, O> handler) {
        var paramsStr = DispatchInvocation.encodeParams(rawParams);

        return CompletableFuture.supplyAsync(
                        () -> {
                            var startNs = System.nanoTime();
                            logger.debug("Handler start: method={}, id={}", method, id);

                            if (session != null) {
                                server.appendEvent(new SessionEvent.RequestEvent(
                                        session.id(), id, method, paramsStr, System.currentTimeMillis()));
                            }

                            var m = server.config().monitoring();
                            var watchdog = m.slowRequestLogging()
                                    ? HandlerWatchdog.watch(
                                            method,
                                            id,
                                            startNs,
                                            m.slowRequestThreshold().toMillis())
                                    : CompletableFuture.completedFuture(null);
                            try {
                                CompletionStage<McpOutcome> stage = OutboundSseStreamMessageRouter.withDispatchContext(
                                        session != null ? session.id() : null,
                                        outboundSseStream,
                                        () -> decodeAndHandleAsync(method, handler, context, rawParams));
                                return stage.whenComplete((r, e) -> watchdog.cancel(false));
                            } catch (Exception e) {
                                watchdog.cancel(false);
                                return CompletableFuture.<McpOutcome>failedFuture(e);
                            }
                        },
                        executor)
                .thenCompose(stage -> stage)
                // handle(), not handleAsync(executor): encoding is a cheap ByteBuf serialize and the
                // completing thread is never the event loop — no need to burn a VT per request on it.
                .handle((outcome, ex) -> ex != null
                        ? errorResult(id, McpOutcomes.classify(id, method, ex), context)
                        : toDispatchResult(id, method, outcome, null, context));
    }

    /**
     * Fixed decode-then-handle skeleton every dispatch path shares -- the single call site each
     * routes through, and the seam {@link McpInterceptor}s wrap around.
     *
     * <p>Classification happens here rather than after the chain unwinds, so an interceptor
     * observes the outcome the wire will actually carry -- above all the JSON-RPC error code, which
     * only the protocol codec can resolve.
     *
     * <p>With no interceptor registered the handler's stage is returned untouched, so the default
     * path neither allocates a chain node nor blocks the dispatch thread on an async handler. The
     * {@link McpInterceptor} seam is synchronous, so once one is registered the chain and the
     * handler share this thread until the outcome exists.
     */
    private <I, O> CompletionStage<McpOutcome> decodeAndHandleAsync(
            String method, RpcMethodHandler<I, O> handler, DispatchContext context, @Nullable Object rawParams) {
        var interceptors = server.interceptors();
        if (interceptors.isEmpty()) {
            return classified(method, handler, context, rawParams);
        }
        return CompletableFuture.completedStage(InterceptorChain.run(
                interceptors,
                new DispatchInvocation(method, context, rawParams),
                context,
                () -> handled(method, handler, context, rawParams)));
    }

    private <I, O> CompletionStage<McpOutcome> classified(
            String method, RpcMethodHandler<I, O> handler, DispatchContext context, @Nullable Object rawParams) {
        return decodeAndHandle(handler, context, rawParams)
                .handle((result, ex) ->
                        ex != null ? McpOutcomes.failure(method, ex, context) : McpOutcomes.of(result, context));
    }

    /**
     * Terminal of the interceptor chain: decodes, handles, and blocks for the result on this
     * virtual thread.
     *
     * <p>ponytail: a handler whose stage stays pending for the life of an SSE subscription
     * ({@code subscriptions/listen}) parks this thread for that long. Bounded by the open
     * connection it belongs to, which costs more than the parked continuation. Split the terminal
     * so a streaming handler returns at stream-establish if that ever shows up in a heap dump.
     */
    private <I, O> McpOutcome handled(
            String method, RpcMethodHandler<I, O> handler, DispatchContext context, @Nullable Object rawParams) {
        try {
            I decoded = handler.decode(context, rawParams);
            return McpOutcomes.of(HandlerFutures.joinInterruptibly(handler.handleAsync(context, decoded)), context);
        } catch (Exception e) {
            return McpOutcomes.failure(method, e, context);
        }
    }

    private <I, O> CompletionStage<O> decodeAndHandle(
            RpcMethodHandler<I, O> handler, DispatchContext context, @Nullable Object rawParams) {
        try {
            I decoded = handler.decode(context, rawParams);
            return handler.handleAsync(context, decoded);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private DispatchResult toDispatchResult(
            RequestId id, String method, McpOutcome outcome, @Nullable String sessionId, DispatchContext context) {
        return switch (outcome) {
            case McpOutcome.Failure(var error, var ignoredCode, var ignoredCause) -> {
                logger.debug("Handler error for {}: {}", method, error.message());
                yield errorResult(id, error, context);
            }
            // A payload failure is a JSON-RPC success on the wire -- the distinction exists for
            // observers, not for the client, so both arms encode identically.
            case McpOutcome.PayloadFailure(var result) -> encodedResponse(id, result, sessionId, context);
            case McpOutcome.Success(var result) -> encodedResponse(id, result, sessionId, context);
        };
    }

    private DispatchResult encodedResponse(
            RequestId id, @Nullable Object result, @Nullable String sessionId, DispatchContext context) {
        return new DispatchResult.Response(encodeResponse(id, result, context.responseMapper()), sessionId, 200);
    }

    public DispatchResult dispatchNotification(String method, @Nullable Object params, @Nullable String sessionId) {
        return dispatchNotification(method, params, sessionId, null);
    }

    public DispatchResult dispatchNotification(
            String method,
            @Nullable Object params,
            @Nullable String sessionId,
            @Nullable ChannelContext channelContext) {
        var interceptors = server.interceptors();
        if (interceptors.isEmpty()) {
            return handleNotification(method, params, sessionId, channelContext);
        }
        // A notification has no response, so its answer is always Accepted: an interceptor can only
        // suppress the handler, never change what the client is told. The transport has already
        // acked before dispatching here (see McpOperationHandler), so blocking the chain is free.
        var context = dispatchContext(channelContext);
        var outcome =
                InterceptorChain.run(interceptors, new DispatchInvocation(method, context, params), context, () -> {
                    handleNotification(method, params, sessionId, channelContext);
                    return new McpOutcome.Success(null);
                });
        if (outcome instanceof McpOutcome.Failure(var error, var ignoredCode, var cause)) {
            if (cause != null) {
                logger.warn("Notification interceptor failed: method={}: {}", method, cause.getMessage(), cause);
            } else {
                logger.debug("Notification rejected by interceptor: method={}: {}", method, error.message());
            }
        }
        return DispatchResult.Accepted.INSTANCE;
    }

    private DispatchResult handleNotification(
            String method,
            @Nullable Object params,
            @Nullable String sessionId,
            @Nullable ChannelContext channelContext) {
        if (server.isStateless()) {
            logger.debug("Stateless notification ignored: {}", method);
            return DispatchResult.Accepted.INSTANCE;
        }
        var context = dispatchContext(channelContext);

        final Optional<Session> sessionOpt;
        if (sessionId != null) {
            sessionOpt = server.getSession(sessionId);
            sessionOpt.ifPresent(Session::touch);
        } else {
            sessionOpt = Optional.empty();
        }
        switch (method) {
            case NOTIFICATIONS_INITIALIZED -> {
                logger.info("Client initialized notification received");
                sessionOpt.ifPresent(session -> {
                    if (session.activate()) {
                        logger.info("Session activated: {}", sessionId);
                    }
                });
                return DispatchResult.Accepted.INSTANCE;
            }
            case NOTIFICATIONS_CANCELLED -> {
                handleCancellation(context, params, sessionId);
                return DispatchResult.Accepted.INSTANCE;
            }
            default -> {}
        }
        logger.debug("Unhandled notification: {}", method);
        return DispatchResult.Accepted.INSTANCE;
    }

    private void handleCancellation(DispatchContext context, @Nullable Object params, @Nullable String sessionId) {
        var cancellation = context.requestMapper().cancellation(params);
        if (cancellation == null) {
            logger.debug("Cancellation notification missing requestId");
            return;
        }
        if (sessionId == null) {
            logger.debug("Cancellation without session, requestId={}", cancellation.requestId());
            return;
        }
        server.getSession(sessionId)
                .ifPresentOrElse(
                        session -> {
                            var reasonMsg = cancellation.reason() != null ? ": " + cancellation.reason() : "";
                            var cancelled = server.failPendingRequest(
                                    cancellation.requestId(), sessionId, null, "Cancelled" + reasonMsg);
                            logger.debug(
                                    "Cancellation received: requestId={}, sessionId={}, reason={}, pending={}",
                                    cancellation.requestId(),
                                    sessionId,
                                    cancellation.reason(),
                                    cancelled);
                            server.appendEvent(new SessionEvent.CancelEvent(
                                    sessionId, cancellation.requestId(), System.currentTimeMillis()));
                        },
                        () -> logger.debug(
                                "Cancellation for unknown session: {}, requestId={}",
                                sessionId,
                                cancellation.requestId()));
    }

    private CompletableFuture<DispatchResult> dispatchInitializeAsync(
            RequestId id, Object rawParams, DispatchContext ic, @Nullable ChannelContext channelContext) {
        logger.debug("Client initialize: id={} stateless={}", id, server.isStateless());
        var handler = server.getHandler("initialize");
        if (handler == null) {
            return CompletableFuture.completedFuture(
                    errorResult(id, ServerErrors.methodNotFound("Method not found: initialize"), ic));
        }
        // Stateful init creates the session before invoking the handler; stateless skips it. Both
        // then share one async pipeline — the response sessionId falls out of ic.session() (null
        // when stateless, since no session was set).
        return CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                if (!server.isStateless()) {
                                    ic.setSession(server.createSession(generateSessionId(channelContext)));
                                }
                                return decodeAndHandleAsync(METHOD_INITIALIZE, handler, ic, rawParams);
                            } catch (Exception e) {
                                return CompletableFuture.<McpOutcome>failedFuture(e);
                            }
                        },
                        executor)
                .thenCompose(stage -> stage)
                .handle((outcome, ex) -> {
                    if (ex != null) {
                        return errorResult(id, McpOutcomes.classify(id, METHOD_INITIALIZE, ex), ic);
                    }
                    final var session = ic.session();
                    var sessionId = session != null ? session.id() : null;
                    return toDispatchResult(id, METHOD_INITIALIZE, outcome, sessionId, ic);
                });
    }

    private DispatchResult errorResult(RequestId id, ServerError error, DispatchContext context) {
        var wireError = context.responseMapper().error(error);
        var body = JsonRpcCodec.serializeError(id, wireError.code(), wireError.message(), wireError.data());
        return new DispatchResult.Response(body, null, wireError.httpStatus());
    }

    private static byte[] encodeResponse(RequestId id, @Nullable Object result, ProtocolResponseMapper mapper) {
        if (result instanceof String s) {
            return JsonRpcCodec.serializeResponse(id, s);
        }
        try {
            var resultJson = mapper.encode(result);
            return JsonRpcCodec.serializeResponse(id, resultJson);
        } catch (Exception e) {
            logger.error(
                    "JSON serialization failed for {}: {}",
                    result == null ? "null" : result.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            return encodeError(id, ServerErrors.internalError("Failed to encode response"), mapper);
        }
    }

    private static byte[] encodeError(@Nullable RequestId id, ServerError error, ProtocolResponseMapper mapper) {
        var wireError = mapper.error(error);
        return JsonRpcCodec.serializeError(id, wireError.code(), wireError.message(), wireError.data());
    }

    private String generateSessionId(@Nullable ChannelContext channelContext) {
        final var request =
                channelContext != null ? channelContext.get(ATTR_INIT_REQUEST).orElse(null) : null;
        final var generator = server.sessionIdGenerator();
        final var id = generator.generate(channelContext, request != null ? request : EMPTY_INIT_REQUEST);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("SessionIdGenerator produced a blank session id");
        }
        return id;
    }
}
