/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.netty;

import static dev.tachyonmcp.transport.netty.ChannelHandlerUtils.*;
import static dev.tachyonmcp.transport.netty.McpResponseWriter.sendJsonResponse;
import static dev.tachyonmcp.transport.netty.McpResponseWriter.sendOptions;

import dev.tachyonmcp.protocol.mcp.McpHeaderNames;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.runtime.InteractionEvent;
import dev.tachyonmcp.server.McpDispatcher;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcMessage;
import dev.tachyonmcp.transport.netty.sse.PostSseStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.timeout.IdleStateEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the INITIALIZATION lifecycle phase of an MCP connection.
 * <p>
 * Receives the first HTTP request on each new channel. If it is an
 * {@code initialize} JSON-RPC request (no {@code MCP-Session-Id} header),
 * the handler dispatches it, passes the resulting session via
 * {@link InteractionEvent.OperationStarted} to bind it into
 * {@link InteractionHandler}'s
 * {@link InteractionContext}, and fires
 * an event to transition the pipeline to the OPERATION phase.
 * <p>
 * All other requests (requests that already carry a session-id, GET, DELETE,
 * OPTIONS, pre-session ping) are immediately forwarded to a new
 * {@link McpOperationHandler} which handles them according to normal operation
 * semantics (including proper rejection of unknown sessions).
 * <p>
 * Not {@code @Sharable} — a fresh instance is added per channel by
 * {@link McpChannelInitializer}.
 */
public class McpInitializationHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(McpInitializationHandler.class);
    private static final String METHOD_INITIALIZE = "initialize";

    private final ServerEngine server;
    private final McpDispatcher dispatcher;
    private final Executor executor;

    public McpInitializationHandler(ServerEngine server, McpDispatcher dispatcher, Executor executor) {
        this.server = server;
        this.dispatcher = dispatcher;
        this.executor = executor;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest req) {
            try {
                handleRequest(ctx, req);
            } finally {
                req.release();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handleRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        var httpMethod = req.method();
        var origin = req.headers().get(HttpHeaderNames.ORIGIN);

        if (httpMethod == HttpMethod.OPTIONS) {
            sendOptions(ctx, origin);
            return;
        }

        if (httpMethod == HttpMethod.POST) {
            var sessionId = req.headers().get(McpHeaderNames.MCP_SESSION_ID);
            if (sessionId == null) {
                captureInitRequest(ctx, req, server);
                handlePostWithoutSession(ctx, req, origin);
                return;
            }
        }

        forwardToOperationHandler(ctx, req);
    }

    private void handlePostWithoutSession(ChannelHandlerContext ctx, FullHttpRequest req, @Nullable String origin) {
        var body = req.content().retain();

        CompletableFuture.runAsync(
                        () -> {
                            final JsonRpcMessage message;
                            try {
                                message = dispatcher.parseMessage(body);
                            } finally {
                                body.release();
                            }
                            dispatchNoSessionMessage(ctx, message, origin);
                        },
                        executor)
                .exceptionally(ex -> {
                    logger.error("Failed to parse POST body during initialization", ex);
                    ctx.executor().execute(() -> {
                        var errorBytes = dispatcher.parseError();
                        sendResponseAndClose(
                                ctx, HttpResponseStatus.BAD_REQUEST, "application/json", errorBytes, origin);
                    });
                    return null;
                });
    }

    private void dispatchNoSessionMessage(
            ChannelHandlerContext ctx, @Nullable JsonRpcMessage message, @Nullable String origin) {
        switch (message) {
            case null ->
                ctx.executor()
                        .execute(() -> sendResponseAndClose(
                                ctx,
                                HttpResponseStatus.BAD_REQUEST,
                                "application/json",
                                dispatcher.parseError(),
                                origin));
            case JsonRpcMessage.Request<?> req
            when METHOD_INITIALIZE.equals(req.method()) -> handleInitialize(ctx, req.id(), req.params(), origin);
            case JsonRpcMessage.Notification<?> not -> {
                ctx.executor().execute(() -> sendAccepted(ctx, origin));
                CompletableFuture.runAsync(
                        () -> dispatcher.dispatchNotification(not.method(), not.params(), null), executor);
            }
            default ->
                // Non-initialize request without session-id: dispatch via operation handler
                // (which will return "Missing MCP-Session-Id" or stateless ping etc.)
                dispatchPreSessionRequest(ctx, message, origin);
        }
    }

    private void dispatchPreSessionRequest(ChannelHandlerContext ctx, JsonRpcMessage message, @Nullable String origin) {
        if (!(message instanceof JsonRpcMessage.Request(Object id, String method, Object params))) {
            ctx.executor().execute(() -> sendAccepted(ctx, origin));
            return;
        }
        var heartbeatInterval = server.config().network().heartbeatInterval();
        var postStream = new PostSseStream(ctx.channel(), origin, server::nextEventId, heartbeatInterval);
        dispatcher
                .dispatchRequestAsync(id, method, params, null, postStream, null)
                .whenComplete((result, ex) -> ctx.executor().execute(() -> {
                    // Neutralize the unused stream so a late server→client message cannot open a
                    // second response on this (potentially keep-alive) channel.
                    postStream.close();
                    if (ex != null) {
                        logger.error("Dispatch failed for pre-session request: method={}", method, ex);
                        sendResponseAndClose(
                                ctx,
                                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                "application/json",
                                dispatcher.parseError(),
                                origin);
                        return;
                    }
                    if (result instanceof McpDispatcher.DispatchResult.Accepted) {
                        sendAccepted(ctx, origin);
                        return;
                    }
                    var response = (McpDispatcher.DispatchResult.Response) result;
                    sendJsonResponse(ctx, response.responseBody(), response.sessionId(), origin);
                }));
    }

    private void handleInitialize(ChannelHandlerContext ctx, Object id, Object params, @Nullable String origin) {
        var heartbeatInterval = server.config().network().heartbeatInterval();
        var postStream = new PostSseStream(ctx.channel(), origin, server::nextEventId, heartbeatInterval);
        final var startNs = System.nanoTime();
        logger.debug("Initialize request: id={}", id);

        dispatcher
                .dispatchRequestAsync(
                        id,
                        METHOD_INITIALIZE,
                        params,
                        null,
                        postStream,
                        ChannelHandlerUtils.requireInteractionContext(ctx))
                .whenComplete((result, ex) -> ctx.executor().execute(() -> {
                    var elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                    // initialize never emits server→client messages, but neutralize defensively so
                    // the keep-alive JSON response below can never be preceded by an SSE upgrade.
                    postStream.close();
                    if (ex != null) {
                        logger.error("Initialize dispatch failed: id={}, elapsed={}ms", id, elapsedMs, ex);
                        sendResponseAndClose(
                                ctx,
                                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                "application/json",
                                dispatcher.parseError(),
                                origin);
                        return;
                    }
                    logger.debug("Initialize response: id={}, elapsed={}ms", id, elapsedMs);

                    var response = (McpDispatcher.DispatchResult.Response) result;
                    var resultSessionId = response.sessionId();

                    // Fire event with the live Session — InteractionHandler binds it into
                    // InteractionContext, and LifecyclePipelineCoordinator replaces this handler
                    // with McpOperationHandler.
                    var mcpSession = resultSessionId != null
                            ? server.getSession(resultSessionId).orElse(null)
                            : null;
                    ctx.pipeline().fireUserEventTriggered(new InteractionEvent.OperationStarted(mcpSession));
                    logger.debug("Pipeline transitioned to OPERATION phase for session: {}", resultSessionId);

                    sendJsonResponse(ctx, response.responseBody(), resultSessionId, origin);
                }));
    }

    /**
     * Fires {@link InteractionEvent.OperationStarted#STATELESS} so the
     * {@link LifecyclePipelineCoordinator} replaces this handler with
     * {@link McpOperationHandler}, then forwards the request to it.
     * The operation handler's own try/finally releases the request;
     * our finally guard prevents a double-release via the {@code refCnt() > 0} check.
     */
    private void forwardToOperationHandler(ChannelHandlerContext ctx, FullHttpRequest req) {
        req.retain();
        try {
            ctx.pipeline().fireUserEventTriggered(InteractionEvent.OperationStarted.STATELESS);
            var opCtx = ctx.pipeline().context(McpHandlerManager.HANDLER_OPS);
            if (opCtx != null) {
                ctx.pipeline().get(McpOperationHandler.class).channelRead(opCtx, req);
            } else {
                logger.warn("Operation handler missing after phase transition; dropping request");
                req.release();
            }
        } catch (Exception e) {
            if (req.refCnt() > 0) {
                req.release();
            }
            throw e;
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            logger.debug(
                    "Idle timeout during initialization, closing channel: {}",
                    ctx.channel().remoteAddress());
            ctx.close();
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // Abrupt close during init phase: fire ShutdownStarted so the coordinator
        // can remove the session (if one was created) and clean up extensions.
        var ic = ctx.channel().attr(InteractionHandler.INTERACTION_CONTEXT_KEY).get();
        if (ic != null && ic.session() != null) {
            ctx.pipeline()
                    .fireUserEventTriggered(
                            new InteractionEvent.ShutdownStarted(ic.session().id()));
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.net.SocketException) {
            logger.debug("Connection reset during initialization", cause);
        } else {
            logger.error("MCP initialization error", cause);
        }
        ctx.close();
    }
}
