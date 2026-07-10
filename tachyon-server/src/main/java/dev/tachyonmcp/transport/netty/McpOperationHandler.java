/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty;

import static dev.tachyonmcp.transport.netty.ChannelHandlerUtils.*;
import static dev.tachyonmcp.transport.netty.McpResponseWriter.*;

import dev.tachyonmcp.protocol.mcp.McpHeaderNames;
import dev.tachyonmcp.runtime.ChannelContext;
import dev.tachyonmcp.runtime.InteractionEvent;
import dev.tachyonmcp.runtime.SseEvent;
import dev.tachyonmcp.server.McpDispatcher;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.server.session.SessionEvent;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcMessage;
import dev.tachyonmcp.transport.netty.sse.PostSseStream;
import dev.tachyonmcp.transport.netty.sse.SseHeartbeat;
import dev.tachyonmcp.transport.netty.sse.SseManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.timeout.IdleStateEvent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles MCP requests during the OPERATION lifecycle phase: all JSON-RPC methods
 * after the initial {@code initialize} handshake. Added to the pipeline either
 * directly (stateless mode) or dynamically by {@link McpInitializationHandler}
 * after a successful initialize.
 *
 * <p>Instantiated per channel (see {@link McpHandlerManager#createOperationHandler()});
 * per-connection state lives in {@link InteractionHandler}'s channel attribute.
 */
public class McpOperationHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(McpOperationHandler.class);

    private final ServerEngine server;
    private final McpDispatcher dispatcher;
    private final Executor executor;
    private final SseManager sseManager;

    public McpOperationHandler(ServerEngine server, McpDispatcher dispatcher, Executor executor) {
        this.server = server;
        this.dispatcher = dispatcher;
        this.executor = executor;
        this.sseManager = new SseManager(server);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest req) {
            try {
                handleRequest(ctx, req);
            } finally {
                if (req.refCnt() > 0) {
                    req.release();
                }
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    void handleRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        var origin = req.headers().get(HttpHeaderNames.ORIGIN);
        logger.debug("MCP request: {} {}", req.method(), req.uri());

        HttpMethod method = req.method();
        if (method == HttpMethod.OPTIONS) {
            sendOptions(ctx, origin);
        } else if (method == HttpMethod.POST) {
            handlePost(ctx, req, origin);
        } else if (method == HttpMethod.GET) {
            handleGet(ctx, req, origin);
        } else if (method == HttpMethod.DELETE) {
            handleDelete(ctx, req, origin);
        } else {
            sendResponseAndClose(
                    ctx,
                    HttpResponseStatus.METHOD_NOT_ALLOWED,
                    "text/plain",
                    ctx.alloc().buffer(0),
                    origin);
        }
    }

    private void handlePost(ChannelHandlerContext ctx, FullHttpRequest req, @Nullable String origin) {
        var sessionId = req.headers().get(McpHeaderNames.MCP_SESSION_ID);
        if (sessionId != null) {
            server.getSession(sessionId).ifPresent(s -> {
                setSession(ctx, s);
            });
        } else {
            // A session-less POST may be an initialize (e.g. on a keep-alive channel already in
            // the operation phase); preserve the request for a custom SessionIdGenerator.
            captureInitRequest(ctx, req, server);
        }
        var body = req.content().retain();
        try {
            CompletableFuture.runAsync(
                            () -> {
                                final JsonRpcMessage message;
                                try {
                                    message = dispatcher.parseMessage(body);
                                } finally {
                                    body.release();
                                }
                                dispatchPostMessage(ctx, sessionId, message, origin);
                            },
                            executor)
                    .exceptionally(ex -> {
                        logger.error("Failed to parse POST body", ex);
                        ctx.executor()
                                .execute(() -> sendResponseAndClose(
                                        ctx,
                                        HttpResponseStatus.BAD_REQUEST,
                                        "application/json",
                                        dispatcher.parseError(),
                                        origin));
                        return null;
                    });
        } catch (RejectedExecutionException e) {
            body.release();
            sendPlainTextAndClose(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "Server shutting down", origin);
        }
    }

    private void dispatchPostMessage(
            ChannelHandlerContext ctx,
            @Nullable String sessionId,
            @Nullable JsonRpcMessage message,
            @Nullable String origin) {
        if (message == null) {
            ctx.executor()
                    .execute(() -> sendResponseAndClose(
                            ctx, HttpResponseStatus.BAD_REQUEST, "application/json", dispatcher.parseError(), origin));
            return;
        }
        switch (message) {
            case JsonRpcMessage.Request<?> reqMsg -> handlePostRequest(ctx, sessionId, reqMsg, origin);
            case JsonRpcMessage.Response resp -> handlePostResponse(ctx, resp, origin);
            case JsonRpcMessage.Error err -> handlePostError(ctx, err, origin);
            case JsonRpcMessage.Notification<?> not -> {
                if (McpDispatcher.NOTIFICATIONS_INITIALIZED.equals(not.method())) {
                    // Activate the session synchronously before acking so a client that waits
                    // for this 202 observes an ACTIVE session on its next request, closing the
                    // INITIALIZING race. Guarded so a handler failure still produces the ack.
                    try {
                        dispatcher.dispatchNotification(not.method(), not.params(), sessionId);
                    } catch (RuntimeException e) {
                        logger.warn("Failed to process {} notification", not.method(), e);
                    }
                    ctx.executor().execute(() -> sendAccepted(ctx, origin));
                } else {
                    ctx.executor().execute(() -> sendAccepted(ctx, origin));
                    CompletableFuture.runAsync(
                            () -> dispatcher.dispatchNotification(not.method(), not.params(), sessionId), executor);
                }
            }
            default -> {
                logger.warn("Unexpected message type: {}", message);
                ctx.executor().execute(() -> sendAccepted(ctx, origin));
            }
        }
    }

    private void handlePostResponse(ChannelHandlerContext ctx, JsonRpcMessage.Response resp, @Nullable String origin) {
        ctx.executor().execute(() -> sendAccepted(ctx, origin));
        executor.execute(() -> {
            if (!server.completePendingRequest(resp.id(), resp.resultJson())) {
                logger.warn("No pending request for response id: {}", resp.id());
            }
        });
    }

    private void handlePostError(ChannelHandlerContext ctx, JsonRpcMessage.Error err, @Nullable String origin) {
        ctx.executor().execute(() -> sendAccepted(ctx, origin));
        executor.execute(() -> {
            if (!server.failPendingRequest(err.id(), err.code() + ": " + err.message())) {
                logger.warn("No pending request for error id: {}", err.id());
            }
        });
    }

    private void handlePostRequest(
            ChannelHandlerContext ctx,
            @Nullable String sessionId,
            JsonRpcMessage.Request req,
            @Nullable String origin) {
        var heartbeatInterval = server.config().network().heartbeatInterval();
        var postStream = new PostSseStream(ctx.channel(), origin, server::nextEventId, heartbeatInterval);
        final var requestId = req.id();
        final var method = req.method();
        final var startNs = System.nanoTime();
        final ChannelContext ic = ChannelHandlerUtils.requireInteractionContext(ctx);
        dispatcher
                .dispatchRequestAsync(requestId, method, req.params(), sessionId, postStream, ic)
                .whenComplete((result, ex) -> {
                    try {
                        ctx.executor()
                                .execute(() -> completePostRequest(
                                        ctx, requestId, method, sessionId, origin, postStream, startNs, result, ex));
                    } catch (RejectedExecutionException e) {
                        releaseResult(result);
                        logger.debug(
                                "Event loop rejected response marshal during shutdown: id={}, method={}",
                                requestId,
                                method);
                    }
                });
    }

    private void completePostRequest(
            ChannelHandlerContext ctx,
            Object requestId,
            String method,
            @Nullable String sessionId,
            @Nullable String origin,
            PostSseStream postStream,
            long startNs,
            McpDispatcher.@Nullable DispatchResult result,
            @Nullable Throwable ex) {
        var elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        var m = server.config().monitoring();
        if (ex != null) {
            logger.error("Dispatch failed: id={}, method={}, elapsed={}ms", requestId, method, elapsedMs, ex);
            if (postStream.started()) {
                postStream.close();
            } else {
                // Neutralize the stream so a late server→client message cannot start a
                // second HTTP response on this channel, then send the JSON error.
                postStream.close();
                sendInternalError(ctx, requestId, origin);
            }
            return;
        }
        if (postStream.started()) {
            if (m.slowRequestLogging() && elapsedMs > m.slowRequestThreshold().toMillis()) {
                logger.warn("Slow POST-SSE response: id={}, method={}, elapsed={}ms", requestId, method, elapsedMs);
            } else {
                logger.debug("POST-SSE response: id={}, method={}, elapsed={}ms", requestId, method, elapsedMs);
            }
            // Finalization decodes the response body and appends to the event log —
            // too heavy for the event loop. PostSseStream writes marshal themselves.
            try {
                executor.execute(() -> finalizePostSseResponse(requestId, sessionId, postStream, result));
            } catch (RejectedExecutionException e) {
                releaseResult(result);
                postStream.close();
            }
            return;
        }
        // A keep-alive JSON/202 response is about to be written; neutralize the unused
        // stream so a server→client message that arrives after this check (e.g. an async
        // tool's status notification) cannot open a second response on the pooled socket
        // and corrupt the next request's reuse of it.
        postStream.close();
        if (result instanceof McpDispatcher.DispatchResult.Accepted) {
            sendAccepted(ctx, origin);
            return;
        }
        if (m.slowRequestLogging() && elapsedMs > m.slowRequestThreshold().toMillis()) {
            logger.warn("Slow POST response: id={}, method={}, elapsed={}ms", requestId, method, elapsedMs);
        } else {
            logger.debug("POST response: id={}, method={}, elapsed={}ms", requestId, method, elapsedMs);
        }
        var response = (McpDispatcher.DispatchResult.Response) result;
        sendJsonResponse(ctx, response.responseBody(), response.sessionId(), origin);
    }

    private static void releaseResult(McpDispatcher.@Nullable DispatchResult result) {
        if (result instanceof McpDispatcher.DispatchResult.Response r) {
            r.responseBody().release();
        }
    }

    private void finalizePostSseResponse(
            Object requestId,
            @Nullable String sessionId,
            PostSseStream postStream,
            McpDispatcher.@Nullable DispatchResult result) {
        if (!(result instanceof McpDispatcher.DispatchResult.Response response)) {
            postStream.close();
            return;
        }
        var responseBody = response.responseBody();
        try {
            var sseEventId = server.nextEventId();
            Runnable onDropped = null;
            if (!server.isStateless()) {
                var resultJson = responseBody.toString(StandardCharsets.UTF_8);
                server.appendEvent(new SessionEvent.ResponseEvent(
                        sessionId,
                        requestId,
                        resultJson,
                        System.currentTimeMillis(),
                        sseEventId,
                        postStream.streamKey()));
                onDropped = redeliverOnReconnect(sessionId, postStream.streamKey(), sseEventId, resultJson);
            }
            postStream.writeEvent(sseEventId, responseBody, onDropped);
            responseBody = null;
        } catch (RuntimeException e) {
            logger.error("Failed to write final response on POST-SSE stream", e);
        } finally {
            if (responseBody != null) {
                responseBody.release();
            }
            postStream.close();
        }
    }

    /**
     * Builds the fallback that runs when the final response could not be written because the tool
     * already closed its POST-SSE stream: if the client has reconnected and explicitly resumed this
     * stream key, deliver the response live on that stream. Otherwise it stays in the event log for
     * {@code Last-Event-ID} replay. Never crosses to a different stream (MCP resumability rule).
     */
    private @Nullable Runnable redeliverOnReconnect(
            @Nullable String sessionId, String streamKey, long sseEventId, String resultJson) {
        if (sessionId == null) {
            return null;
        }
        var wireId = ServerEngine.wireEventId(sseEventId, streamKey);
        return () -> server.getSession(sessionId).ifPresent(session -> {
            // ponytail: the resumed reconnect may also replay this event from the log, so a rare
            // race can deliver it twice with the same SSE id — harmless (the client dedupes the
            // JSON-RPC response by request id). Per-connection id de-dup if that ever bites.
            if (streamKey.equals(session.resumingStreamKey())) {
                session.send(new SseEvent(wireId, "message", resultJson));
            }
        });
    }

    private void handleGet(ChannelHandlerContext ctx, FullHttpRequest req, @Nullable String origin) {
        if (server.isStateless()) {
            sseManager.openStatelessStream(ctx, origin);
            return;
        }
        var sessionId = req.headers().get(McpHeaderNames.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isEmpty()) {
            sendPlainTextAndClose(ctx, HttpResponseStatus.BAD_REQUEST, "Missing MCP-Session-Id header", origin);
            return;
        }
        var sessionOpt = server.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            sendPlainTextAndClose(ctx, HttpResponseStatus.BAD_REQUEST, "Unknown session", origin);
            return;
        }
        sseManager.openStream(ctx, sessionOpt.get(), req.headers().get(McpHeaderNames.LAST_EVENT_ID), origin);
    }

    private void handleDelete(ChannelHandlerContext ctx, FullHttpRequest req, @Nullable String origin) {
        var sessionId = req.headers().get(McpHeaderNames.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isEmpty()) {
            sendPlainTextAndClose(ctx, HttpResponseStatus.BAD_REQUEST, "Missing MCP-Session-Id header", origin);
            return;
        }
        // Fire ShutdownStarted before sending the response so the session is removed
        // before the client receives the OK (eliminates race on server.session()).
        ctx.pipeline().fireUserEventTriggered(new InteractionEvent.ShutdownStarted(sessionId));
        sendPlainTextAndClose(ctx, HttpResponseStatus.OK, "", origin);
        logger.info("Session terminated via DELETE: {}", sessionId);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        var writable = ctx.channel().isWritable();
        ctx.channel().config().setAutoRead(writable);
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            if (!SseHeartbeat.isEnabled(ctx.channel())) {
                logger.debug("Idle timeout, closing channel: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
            // SSE channels: idle tick is a no-op — the scheduler drives heartbeats.
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.net.SocketException) {
            logger.debug("Connection reset on MCP endpoint", cause);
        } else {
            logger.error("MCP endpoint error", cause);
        }
        ctx.close();
    }
}
