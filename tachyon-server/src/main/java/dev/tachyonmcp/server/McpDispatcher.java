/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.CodecRegistry;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeRequestParams;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.runtime.SessionState;
import dev.tachyonmcp.server.session.DefaultMcpContext;
import dev.tachyonmcp.server.session.McpSession;
import dev.tachyonmcp.server.session.SessionEvent;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcError;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcMessage;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the MCP server's per-request flow: parses JSON-RPC messages, establishes the session on
 * {@code initialize}, routes to registered handlers (including extension methods), tracks pending
 * requests, and encodes responses. Collaborator of {@link McpServer} — server holds state/registries,
 * this drives one request at a time.
 *
 * <p>MCP- and spec-version-specific: it special-cases {@code initialize}/task-status and binds the
 * {@code v2025_11_25} models/codecs. The version-specific call-sites (marked below) move behind an
 * {@code McpDialect} when a second spec version is wired.
 */
public class McpDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(McpDispatcher.class);

    private static final long SLOW_HANDLER_MS = 200;

    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_PING = "ping";
    public static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    private static final String NOTIFICATIONS_CANCELLED = "notifications/cancelled";
    private static final String NOTIFICATIONS_TASKS_STATUS = "notifications/tasks/status";

    private final Executor executor;

    private final McpServer server;

    public McpDispatcher(McpServer server, Executor executor) {
        this.server = server;
        this.executor = executor;
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
            @Nullable InteractionContext ic) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(method, "method");

        if (METHOD_INITIALIZE.equals(method)) {
            if (sessionId == null) {
                return dispatchInitializeAsync(id, params, ic);
            }
            // initialize with an existing session ID → reject (E2: prevents re-initialization)
            var err = JsonRpcErrors.invalidRequest("Session already initialized");
            return CompletableFuture.completedFuture(errorResult(id, err.code(), err.message()));
        }

        if (server.isStateless() || (METHOD_PING.equals(method) && sessionId == null)) {
            return dispatchStatelessAsync(id, method, params, outboundSseStream);
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

        var sessionState = session.state();
        if (sessionState == SessionState.CLOSED) {
            return CompletableFuture.completedFuture(
                    errorResult(id, JsonRpcErrors.INVALID_REQUEST, "Session is closed"));
        }
        if (sessionState == SessionState.INITIALIZING && !METHOD_PING.equals(method)) {
            return CompletableFuture.completedFuture(
                    errorResult(id, JsonRpcErrors.INVALID_REQUEST, "Session is not yet active, only ping allowed"));
        }

        var paramsStr = params instanceof Map || params instanceof List
                ? JsonRpcCodec.writeValueAsString(params)
                : params instanceof String s ? s : null;

        var owningExtensionId = server.extensionForMethod(method);
        if (owningExtensionId != null) {
            var extEnabled = ic != null
                    ? ic.isExtensionEnabled(owningExtensionId)
                    : session.isExtensionEnabled(owningExtensionId);
            if (!extEnabled) {
                return CompletableFuture.completedFuture(
                        errorResult(id, JsonRpcErrors.METHOD_NOT_FOUND, "Method not found"));
            }
            if (server.extensionRequiresMeta(owningExtensionId) && !hasMetaKey(params, owningExtensionId)) {
                return CompletableFuture.completedFuture(
                        errorResult(id, JsonRpcErrors.METHOD_NOT_FOUND, "Method not found"));
            }
        }

        var handler = server.getHandler(method);
        if (handler == null) {
            var err = JsonRpcErrors.methodNotFound("Method not found");
            return CompletableFuture.completedFuture(errorResult(id, err.code(), err.message()));
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    // E12: hold read lock so concurrent session.close() (write lock) must wait
                    var readLock = session.lock().readLock();
                    readLock.lock();
                    try {
                        if (session.state() == SessionState.CLOSED) {
                            return errorResult(id, JsonRpcErrors.INVALID_REQUEST, "Session closed");
                        }
                        server.appendEvent(new SessionEvent.RequestEvent(
                                session.id(), id, method, paramsStr, System.currentTimeMillis()));
                        var thread = Thread.currentThread();
                        var startNs = System.nanoTime();
                        logger.debug(
                                "Handler start: method={}, id={}, thread={}#{} virtual={}",
                                method,
                                id,
                                thread.getName(),
                                thread.threadId(),
                                thread.isVirtual());

                        var watchdog = HandlerWatchdog.watch(method, id, startNs, thread, SLOW_HANDLER_MS);

                        try {
                            Object resultValue;
                            var context = new DefaultMcpContext(session, server, ic);
                            resultValue = OutboundSseStreamMessageRouter.withDispatchContext(
                                    session.id(), outboundSseStream, () -> handler.handle(context, params));
                            var elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                            watchdog.cancel(false);
                            if (elapsedMs > SLOW_HANDLER_MS) {
                                logger.debug(
                                        "Handler completed (was slow): method={}, id={}, elapsed={}ms,"
                                                + " thread={}#{} virtual={}",
                                        method,
                                        id,
                                        elapsedMs,
                                        thread.getName(),
                                        thread.threadId(),
                                        thread.isVirtual());
                            } else {
                                logger.debug("Handler done: method={}, id={}, elapsed={}ms", method, id, elapsedMs);
                            }
                            if (resultValue instanceof JsonRpcError error) {
                                logger.debug("Handler error for {}: {}", method, error.message());
                                return errorResult(id, error.code(), error.message());
                            }
                            return new DispatchResult.Response(encodeResponse(id, resultValue), null);
                        } catch (Exception e) {
                            var elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                            watchdog.cancel(false);
                            logger.warn(
                                    "Handler exception: method={}, id={}, elapsed={}ms,"
                                            + " thread={}#{} virtual={}: {}",
                                    method,
                                    id,
                                    elapsedMs,
                                    thread.getName(),
                                    thread.threadId(),
                                    thread.isVirtual(),
                                    e.getMessage(),
                                    e);
                            return errorResult(id, JsonRpcErrors.INTERNAL_ERROR, "Internal error");
                        }
                    } finally {
                        readLock.unlock();
                    }
                },
                executor);
    }

    private CompletableFuture<DispatchResult> dispatchStatelessAsync(
            Object id, String method, Object params, @Nullable OutboundSseStream outboundSseStream) {
        var owningExtensionId = server.extensionForMethod(method);
        if (owningExtensionId != null) {
            return CompletableFuture.completedFuture(
                    errorResult(id, JsonRpcErrors.METHOD_NOT_FOUND, "Method not found"));
        }
        var handler = server.getHandler(method);
        if (handler == null) {
            var err = JsonRpcErrors.methodNotFound("Method not found");
            return CompletableFuture.completedFuture(errorResult(id, err.code(), err.message()));
        }
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        var context = DefaultMcpContext.stateless(server);
                        var result = OutboundSseStreamMessageRouter.withDispatchContext(
                                null, outboundSseStream, () -> handler.handle(context, params));
                        if (result instanceof JsonRpcError error) {
                            return errorResult(id, error.code(), error.message());
                        }
                        return new DispatchResult.Response(encodeResponse(id, result), null);
                    } catch (Exception e) {
                        logger.warn("Stateless handler exception: method={}", method, e);
                        return errorResult(id, JsonRpcErrors.INTERNAL_ERROR, "Internal error");
                    }
                },
                executor);
    }

    public DispatchResult dispatchNotification(String method, @Nullable Object params, @Nullable String sessionId) {
        if (server.isStateless()) {
            logger.debug("Stateless notification ignored: {}", method);
            return DispatchResult.Accepted.INSTANCE;
        }

        if (sessionId != null) {
            server.getSession(sessionId).ifPresent(McpSession::touch);
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
        var rawStatusMessage = map.get("statusMessage");
        var statusMessage = rawStatusMessage instanceof String s ? s : null;
        var taskRegistry = server.tasks();
        taskRegistry.updateStatusFromClientNotification(taskId, taskStatus, statusMessage);
    }

    private CompletableFuture<DispatchResult> dispatchInitializeAsync(
            Object id, Object rawParams, @Nullable InteractionContext ic) {
        return CompletableFuture.supplyAsync(
                () -> {
                    logger.debug("Client initialize: id={} stateless={}", id, server.isStateless());
                    var handler = server.getHandler("initialize");
                    // version-specific (MCP 2025-11-25): handshake params shape — moves behind McpDialect
                    var typedParams = convertParams(rawParams, InitializeRequestParams.class);
                    if (handler == null) {
                        return errorResult(id, JsonRpcErrors.METHOD_NOT_FOUND, "Method not found: initialize");
                    }
                    if (server.isStateless()) {
                        try {
                            var result = handler.handle(DefaultMcpContext.stateless(server), typedParams);
                            if (result instanceof JsonRpcError error) {
                                logger.debug("Initialize handler error (stateless): {}", error.message());
                                return errorResult(id, error.code(), error.message());
                            }
                            return new DispatchResult.Response(encodeResponse(id, result), null);
                        } catch (Exception e) {
                            logger.warn("Initialize handler exception (stateless)", e);
                            return errorResult(id, JsonRpcErrors.INTERNAL_ERROR, "Internal error");
                        }
                    }
                    var sessionId = generateSessionId();
                    var session = server.createSession(sessionId);
                    try {
                        var context = new DefaultMcpContext(session, server, ic);
                        var result = handler.handle(context, typedParams);
                        if (result instanceof JsonRpcError error) {
                            logger.debug("Initialize handler error: {}", error.message());
                            return errorResult(id, error.code(), error.message());
                        }
                        return new DispatchResult.Response(encodeResponse(id, result), sessionId);
                    } catch (Exception e) {
                        logger.warn("Initialize handler exception", e);
                        return errorResult(id, JsonRpcErrors.INTERNAL_ERROR, "Internal error");
                    }
                },
                executor);
    }

    private static <T> T convertParams(Object rawParams, Class<T> targetType) {
        if (rawParams == null) return null;
        if (targetType.isInstance(rawParams)) return targetType.cast(rawParams);
        if (rawParams instanceof Map<?, ?> map) {
            var json = JsonRpcCodec.writeValueAsString(map);
            return JsonRpcCodec.decodeWithCodec(json, targetType);
        }
        return null;
    }

    private DispatchResult errorResult(Object id, int code, String message) {
        return new DispatchResult.Response(JsonRpcCodec.serializeError(id, code, message, null), null);
    }

    private static ByteBuf encodeResponse(Object id, Object result) {
        if (result instanceof String s) {
            return JsonRpcCodec.serializeResponse(id, s);
        }
        // version-specific (MCP 2025-11-25): codec registry — moves behind McpDialect
        var codec = CodecRegistry.codecFor(result.getClass());
        if (codec != null) {
            try {
                return JsonRpcCodec.serializeResponse(id, codec, result);
            } catch (Exception e) {
                logger.error("Codec encode failed for {}: {}", result.getClass().getSimpleName(), e.getMessage(), e);
                return JsonRpcCodec.serializeError(id, JsonRpcErrors.INTERNAL_ERROR, "Failed to encode response", null);
            }
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

    private static String generateSessionId() {
        return "sess_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static boolean hasMetaKey(Object params, String key) {
        if (params instanceof Map<?, ?> map && map.get("_meta") instanceof Map<?, ?> meta) {
            return meta.containsKey(key);
        }
        return false;
    }
}
