/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import static dev.tachyonmcp.transport.netty.ChannelHandlerUtils.*;
import static dev.tachyonmcp.transport.netty.McpResponseWriter.*;

import dev.tachyonmcp.runtime.InteractionEvent;
import dev.tachyonmcp.runtime.McpHeaderNames;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.server.McpDispatcher;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.session.DispatchContext;
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

    private final Server server;
    private final McpDispatcher dispatcher;
    private final Executor executor;
    private final SseManager sseManager;

    public McpOperationHandler(Server server, McpDispatcher dispatcher, Executor executor) {
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
            server.getSession(sessionId).ifPresent(Session::touch);
        }
        var body = req.content().retain();
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
        var postStream = new PostSseStream(ctx.channel(), origin, server::nextEventId);
        final var requestId = req.id();
        final var method = req.method();
        final var startNs = System.nanoTime();
        final DispatchContext ic = ChannelHandlerUtils.requireInteractionContext(ctx);
        dispatcher
                .dispatchRequestAsync(requestId, method, req.params(), sessionId, postStream, ic)
                .whenComplete((result, ex) -> ctx.executor().execute(() -> {
                    var elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                    if (ex != null) {
                        logger.error(
                                "Dispatch failed: id={}, method={}, elapsed={}ms", requestId, method, elapsedMs, ex);
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
                        finalizePostSseResponse(requestId, sessionId, postStream, result);
                        if (elapsedMs > 500) {
                            logger.warn(
                                    "Slow POST-SSE response: id={}, method={}, elapsed={}ms",
                                    requestId,
                                    method,
                                    elapsedMs);
                        } else {
                            logger.debug(
                                    "POST-SSE response: id={}, method={}, elapsed={}ms", requestId, method, elapsedMs);
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
                    if (elapsedMs > 500) {
                        logger.warn("Slow POST response: id={}, method={}, elapsed={}ms", requestId, method, elapsedMs);
                    } else {
                        logger.debug("POST response: id={}, method={}, elapsed={}ms", requestId, method, elapsedMs);
                    }
                    var response = (McpDispatcher.DispatchResult.Response) result;
                    sendJsonResponse(ctx, response.responseBody(), response.sessionId(), origin);
                }));
    }

    private void finalizePostSseResponse(
            Object requestId,
            @Nullable String sessionId,
            PostSseStream postStream,
            McpDispatcher.DispatchResult result) {
        if (!(result instanceof McpDispatcher.DispatchResult.Response response)) {
            postStream.close();
            return;
        }
        var responseBody = response.responseBody();
        try {
            var sseEventId = server.nextEventId();
            if (!server.isStateless()) {
                var resultJson = responseBody.toString(StandardCharsets.UTF_8);
                server.appendEvent(new SessionEvent.ResponseEvent(
                        sessionId, requestId, resultJson, System.currentTimeMillis(), sseEventId));
            }
            postStream.writeEvent(sseEventId, responseBody);
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
            if (SseHeartbeat.isEnabled(ctx.channel())) {
                // Long-lived SSE stream: a silent client is normal (it only reads), so an idle tick
                // means "keep alive", not "dead". Emit a comment heartbeat instead of closing; a
                // failed heartbeat write reaps a genuinely dead client.
                SseHeartbeat.send(ctx.channel());
            } else {
                logger.debug("Idle timeout, closing channel: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
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
