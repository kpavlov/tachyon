/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus;
import dev.tachyonmcp.runtime.MutableInteractionContext;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SessionState;
import dev.tachyonmcp.server.features.tasks.TaskState;
import dev.tachyonmcp.server.session.DefaultMcpContext;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.server.session.SessionEvent;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcError;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcMessage;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the MCP server's per-request flow: parses JSON-RPC messages, establishes the session on
 * {@code initialize}, routes to registered handlers (including extension methods), tracks pending
 * requests, and encodes responses. Collaborator of {@link Server} — server holds state/registries,
 * this drives one request at a time.
 *
 * <p>MCP- and spec-version-specific: it special-cases {@code initialize}/task-status and binds the
 * {@code v2025_11_25} models/codecs. The version-specific call-sites (marked below) move behind an
 * {@code McpDialect} when a second spec version is wired.
 */
public class RpcDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(RpcDispatcher.class);

    private static final long SLOW_HANDLER_MS = 200;

    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_PING = "ping";

    /**
     * Interaction-context attribute key under which {@link dev.tachyonmcp.transport.netty.McpInitializationHandler} stashes a
     * detached copy of the {@code initialize} HTTP request, so a custom
     * {@link dev.tachyonmcp.server.session.SessionIdGenerator} can read its headers/URI.
     */
    public static final String ATTR_INIT_REQUEST = "init.request";

    /**
     * Placeholder request for programmatic dispatch with no channel (the default generator ignores it).
     */
    private static final HttpRequest EMPTY_INIT_REQUEST =
            new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");

    public static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    private static final String NOTIFICATIONS_CANCELLED = "notifications/cancelled";
    private static final String NOTIFICATIONS_TASKS_STATUS = "notifications/tasks/status";

    private final Executor executor;

    private final Server server;

    public RpcDispatcher(Server server, Executor executor) {
        this.server = server;
        this.executor = executor;
    }

    /**
     * Decorates the per-channel context with the per-request MCP dispatch surface. Without a channel
     * context (direct invocation, tests), fresh channel state is created for the default protocol.
     */
    private DispatchContext dispatchContext(@Nullable MutableInteractionContext channelContext) {
        var channel = channelContext != null
                ? channelContext
                : Protocols.versions().getFirst().createInteractionContext();
        return new DefaultMcpContext(channel, server);
    }

    public sealed interface DispatchResult permits DispatchResult.Accepted, DispatchResult.Response {

        record Accepted() implements DispatchResult {
            static final Accepted INSTANCE = new Accepted();
        }

        record Response(ByteBuf responseBody, @Nullable String sessionId) implements DispatchResult {}
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

    public ByteBuf parseError() {
        var err = JsonRpcErrors.parseError();
        return JsonRpcCodec.serializeError(-1, err.code(), err.message(), null);
    }

    public CompletableFuture<DispatchResult> dispatchRequestAsync(
            Object id, String method, Object params, @Nullable String sessionId) {
        return dispatchRequestAsync(id, method, params, sessionId, null, null);
    }

    public CompletableFuture<DispatchResult> dispatchRequestAsync(
            Object id,
            String method,
            Object params,
            @Nullable String sessionId,
            @Nullable OutboundSseStream outboundSseStream,
            @Nullable MutableInteractionContext channelContext) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(method, "method");

        if (METHOD_INITIALIZE.equals(method)) {
            if (sessionId == null) {
                return dispatchInitializeAsync(id, params, dispatchContext(channelContext), channelContext);
            }
            var err = JsonRpcErrors.invalidRequest("Session already initialized");
            return CompletableFuture.completedFuture(errorResult(id, err.code(), err.message()));
        }

        if (server.isStateless() || (METHOD_PING.equals(method) && sessionId == null)) {
            var statelessIc = dispatchContext(channelContext);
            statelessIc.setOutboundStream(outboundSseStream);
            var handler = lookupHandler(method, params, statelessIc);
            if (handler == null) {
                return CompletableFuture.completedFuture(
                        errorResult(id, JsonRpcErrors.METHOD_NOT_FOUND, "Method not found"));
            }
            return invokeHandlerAsync(id, method, params, outboundSseStream, statelessIc, null, handler);
        }

        if (sessionId == null) {
            var err = JsonRpcErrors.invalidRequest("Missing MCP-Session-Id header");
            return CompletableFuture.completedFuture(errorResult(id, err.code(), err.message()));
        }

        var sessionOpt = server.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            var err = JsonRpcErrors.invalidRequest("Unknown session");
            return CompletableFuture.completedFuture(errorResult(id, err.code(), err.message()));
        }
        var session = sessionOpt.get();
        session.touch();

        var requestCtx = dispatchContext(channelContext);
        requestCtx.setSession(session);
        requestCtx.setOutboundStream(outboundSseStream);

        var sessionState = session.state();
        if (sessionState == SessionState.CLOSED) {
            return CompletableFuture.completedFuture(
                    errorResult(id, JsonRpcErrors.INVALID_REQUEST, "Session is closed"));
        }
        if (sessionState == SessionState.INITIALIZING && !METHOD_PING.equals(method)) {
            return CompletableFuture.completedFuture(
                    errorResult(id, JsonRpcErrors.INVALID_REQUEST, "Session is not yet active, only ping allowed"));
        }

        var handler = lookupHandler(method, params, requestCtx);
        if (handler == null) {
            return CompletableFuture.completedFuture(
                    errorResult(id, JsonRpcErrors.METHOD_NOT_FOUND, "Method not found"));
        }

        return invokeHandlerAsync(id, method, params, outboundSseStream, requestCtx, session, handler);
    }

    private @Nullable RpcMethodHandler lookupHandler(String method, Object params, DispatchContext ic) {
        var owningExtensionId = server.extensionForMethod(method);
        if (owningExtensionId != null) {
            if (!ic.isExtensionEnabled(owningExtensionId)) return null;
            if (server.extensionRequiresMeta(owningExtensionId) && !hasMetaKey(params, owningExtensionId)) return null;
        }
        return server.getHandler(method);
    }

    private CompletableFuture<DispatchResult> invokeHandlerAsync(
            Object id,
            String method,
            Object params,
            @Nullable OutboundSseStream outboundSseStream,
            DispatchContext context,
            @Nullable Session session,
            RpcMethodHandler handler) {
        var paramsStr = params instanceof Map || params instanceof List
                ? JsonRpcCodec.writeValueAsString(params)
                : params instanceof String s ? s : null;

        return CompletableFuture.supplyAsync(
                        () -> {
                            var startNs = System.nanoTime();
                            var thread = Thread.currentThread();
                            logger.debug(
                                    "Handler start: method={}, id={}, thread={}#{} virtual={}",
                                    method,
                                    id,
                                    thread.getName(),
                                    thread.threadId(),
                                    thread.isVirtual());

                            if (session != null) {
                                server.appendEvent(new SessionEvent.RequestEvent(
                                        session.id(), id, method, paramsStr, System.currentTimeMillis()));
                            }

                            var watchdog = HandlerWatchdog.watch(method, id, startNs, SLOW_HANDLER_MS);
                            try {
                                return OutboundSseStreamMessageRouter.withDispatchContext(
                                        session != null ? session.id() : null, outboundSseStream, () -> {
                                            try {
                                                return handler.handleAsync(context, params);
                                            } catch (Exception e) {
                                                return CompletableFuture.failedFuture(e);
                                            }
                                        });
                            } catch (Exception e) {
                                return CompletableFuture.failedFuture(e);
                            }
                        },
                        executor)
                .thenCompose(Function.identity())
                .handleAsync(
                        (result, ex) -> {
                            if (ex != null) {
                                return handleHandlerError(id, method, ex);
                            }
                            return handleSuccessOrJsonRpcError(id, method, result, null);
                        },
                        executor);
    }

    private DispatchResult handleHandlerError(Object id, String method, Throwable ex) {
        var unwrapped = ex instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : ex;
        if (unwrapped instanceof CancellationException) {
            logger.debug("Handler cancelled: method={}, id={}", method, id);
            return errorResult(id, JsonRpcErrors.INTERNAL_ERROR, "Internal error");
        }
        logger.warn("Handler exception: method={}, id={}: {}", method, id, unwrapped.getMessage(), unwrapped);
        return errorResult(id, JsonRpcErrors.INTERNAL_ERROR, "Internal error");
    }

    private DispatchResult handleSuccessOrJsonRpcError(
            Object id, String method, Object result, @Nullable String sessionId) {
        if (result instanceof JsonRpcError error) {
            logger.debug("Handler error for {}: {}", method, error.message());
            return errorResult(id, error.code(), error.message());
        }
        return new DispatchResult.Response(encodeResponse(id, result), sessionId);
    }

    public DispatchResult dispatchNotification(String method, @Nullable Object params, @Nullable String sessionId) {
        if (server.isStateless()) {
            logger.debug("Stateless notification ignored: {}", method);
            return DispatchResult.Accepted.INSTANCE;
        }

        if (sessionId != null) {
            server.getSession(sessionId).ifPresent(Session::touch);
        }
        switch (method) {
            case NOTIFICATIONS_INITIALIZED -> {
                logger.info("Client initialized notification received");
                server.getSession(sessionId).ifPresent(session -> {
                    if (session.activate()) {
                        logger.info("Session activated: {}", sessionId);
                    }
                });
                return DispatchResult.Accepted.INSTANCE;
            }
            case NOTIFICATIONS_CANCELLED -> {
                handleCancellation(params, sessionId);
                return DispatchResult.Accepted.INSTANCE;
            }
            case NOTIFICATIONS_TASKS_STATUS -> {
                handleTaskStatusNotification(params, sessionId);
                return DispatchResult.Accepted.INSTANCE;
            }
            default -> {}
        }
        logger.debug("Unhandled notification: {}", method);
        return DispatchResult.Accepted.INSTANCE;
    }

    private void handleCancellation(@Nullable Object params, @Nullable String sessionId) {
        Object rawRequestId = null;
        String rawReason = null;
        if (params instanceof Map<?, ?> map) {
            rawRequestId = map.get("requestId");
            var r = map.get("reason");
            rawReason = r instanceof String s ? s : null;
        }
        if (rawRequestId == null) {
            logger.debug("Cancellation notification missing requestId");
            return;
        }
        if (sessionId == null) {
            logger.debug("Cancellation without session, requestId={}", rawRequestId);
            return;
        }
        var finalRequestId = rawRequestId;
        var finalReason = rawReason;
        server.getSession(sessionId)
                .ifPresentOrElse(
                        session -> {
                            var reasonMsg = finalReason != null ? ": " + finalReason : "";
                            var cancelled = server.failPendingRequest(finalRequestId, "Cancelled" + reasonMsg);
                            if (cancelled) {
                                logger.info(
                                        "Cancelled pending request: requestId={}, sessionId={}, reason={}",
                                        finalRequestId,
                                        sessionId,
                                        finalReason);
                            } else {
                                logger.debug(
                                        "No pending request found for cancellation: requestId={}, sessionId={}",
                                        finalRequestId,
                                        sessionId);
                            }
                            server.appendEvent(new SessionEvent.CancelEvent(
                                    sessionId, finalRequestId, System.currentTimeMillis()));
                        },
                        () -> logger.debug(
                                "Cancellation for unknown session: {}, requestId={}", sessionId, finalRequestId));
    }

    private void handleTaskStatusNotification(@Nullable Object params, @Nullable String sessionId) {
        if (sessionId == null) {
            logger.debug("Task status notification without session");
            return;
        }
        if (!(params instanceof Map<?, ?> map)) {
            logger.debug("Task status notification missing params");
            return;
        }
        var rawTaskId = map.get("taskId");
        if (!(rawTaskId instanceof String taskId)) {
            logger.debug("Task status notification missing taskId");
            return;
        }
        // version-specific (MCP 2025-11-25): task status enum — moves behind McpDialect
        TaskStatus taskStatus = null;
        var rawStatus = map.get("status");
        if (rawStatus instanceof String s) {
            try {
                taskStatus = TaskStatus.fromValue(s);
            } catch (IllegalArgumentException e) {
                logger.debug("Unknown task status: {}", s);
            }
        } else if (rawStatus instanceof TaskStatus ts) {
            taskStatus = ts;
        }
        if (taskStatus == null) {
            taskStatus = TaskStatus.WORKING;
        }
        var newStatus =
                switch (taskStatus) {
                    case WORKING -> TaskState.WORKING;
                    case INPUT_REQUIRED -> TaskState.INPUT_REQUIRED;
                    case COMPLETED -> TaskState.COMPLETED;
                    case FAILED -> TaskState.FAILED;
                    case CANCELLED -> TaskState.CANCELLED;
                };
        var rawStatusMessage = map.get("statusMessage");
        var statusMessage = rawStatusMessage instanceof String s ? s : null;
        var taskRegistry = server.tasks();
        taskRegistry.updateStatusFromClientNotification(taskId, newStatus, statusMessage);
    }

    private CompletableFuture<DispatchResult> dispatchInitializeAsync(
            Object id, Object rawParams, DispatchContext ic, @Nullable MutableInteractionContext channelContext) {
        logger.debug("Client initialize: id={} stateless={}", id, server.isStateless());
        var handler = server.getHandler("initialize");
        if (handler == null) {
            return CompletableFuture.completedFuture(
                    errorResult(id, JsonRpcErrors.METHOD_NOT_FOUND, "Method not found: initialize"));
        }
        if (server.isStateless()) {
            return CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return handler.handleAsync(ic, rawParams);
                                } catch (Exception e) {
                                    return CompletableFuture.failedFuture(e);
                                }
                            },
                            executor)
                    .thenCompose(Function.identity())
                    .handleAsync(
                            (result, ex) -> {
                                if (ex != null) {
                                    logger.warn("Initialize handler exception (stateless)", ex);
                                    return errorResult(id, JsonRpcErrors.INTERNAL_ERROR, "Internal error");
                                }
                                return handleSuccessOrJsonRpcError(id, "initialize", result, null);
                            },
                            executor);
        }
        return CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                var sessionId = generateSessionId(channelContext);
                                var session = server.createSession(sessionId);
                                ic.setSession(session);
                                return handler.handleAsync(ic, rawParams);
                            } catch (Exception e) {
                                return CompletableFuture.failedFuture(e);
                            }
                        },
                        executor)
                .thenCompose(Function.identity())
                .handleAsync(
                        (result, ex) -> {
                            if (ex != null) {
                                logger.warn("Initialize handler exception", ex);
                                return errorResult(id, JsonRpcErrors.INTERNAL_ERROR, "Internal error");
                            }
                            var sessionId = ic.session() != null ? ic.session().id() : null;
                            return handleSuccessOrJsonRpcError(id, "initialize", result, sessionId);
                        },
                        executor);
    }

    private DispatchResult errorResult(Object id, int code, String message) {
        return new DispatchResult.Response(JsonRpcCodec.serializeError(id, code, message, null), null);
    }

    private static ByteBuf encodeResponse(Object id, Object result) {
        if (result instanceof String s) {
            return JsonRpcCodec.serializeResponse(id, s);
        }
        try {
            var resultJson = JsonRpcCodec.writeValueAsString(result);
            return JsonRpcCodec.serializeResponse(id, resultJson);
        } catch (Exception e) {
            logger.error(
                    "JSON serialization failed for {}: {}", result.getClass().getSimpleName(), e.getMessage(), e);
            return JsonRpcCodec.serializeError(id, JsonRpcErrors.INTERNAL_ERROR, "Failed to encode response", null);
        }
    }

    private String generateSessionId(@Nullable MutableInteractionContext channelContext) {
        var request = channelContext != null ? channelContext.<HttpRequest>getAttribute(ATTR_INIT_REQUEST) : null;
        var generator = server.sessionIdGenerator();
        return generator.generate(request != null ? request : EMPTY_INIT_REQUEST);
    }

    private static boolean hasMetaKey(Object params, String key) {
        if (params instanceof Map<?, ?> map && map.get("_meta") instanceof Map<?, ?> meta) {
            return meta.containsKey(key);
        }
        return false;
    }
}
