/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TaskStatus;
import dev.tachyonmcp.runtime.SessionState;
import dev.tachyonmcp.server.features.tasks.TaskState;
import dev.tachyonmcp.server.session.DefaultMcpContext;
import dev.tachyonmcp.server.session.McpContext;
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
import java.util.concurrent.CancellationException;
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
/** Dispatches parsed JSON-RPC messages to registered method handlers and manages the dispatch lifecycle. */
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
        McpContext ic = null;
        if (sessionId != null) {
            var sessionOpt = server.getSession(sessionId);
            if (sessionOpt.isPresent()) {
                ic = new DefaultMcpContext(Protocols.versions().get(0), server);
                ic.setSession(sessionOpt.get());
            }
        }
        return dispatchRequestAsync(id, method, params, sessionId, null, ic);
    }

    public CompletableFuture<DispatchResult> dispatchRequestAsync(
            Object id,
            String method,
            Object params,
            @Nullable String sessionId,
            @Nullable OutboundSseStream outboundSseStream,
            McpContext ic) {
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

        // Always bind the current session and outbound stream to the IC per-request
        // (keep-alive connections reuse the channel IC across multiple sessions)
        if (ic != null) {
            ic.setSession(session);
            ic.setOutboundStream(outboundSseStream);
        }

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
            if (!ic.isExtensionEnabled(owningExtensionId)) {
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
                            resultValue = OutboundSseStreamMessageRouter.withDispatchContext(
                                    session.id(), outboundSseStream, () -> handler.handle(ic, params));
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
                        } catch (CancellationException e) {
                            var elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                            watchdog.cancel(false);
                            logger.debug("Handler cancelled: method={}, id={}, elapsed={}ms", method, id, elapsedMs);
                            return errorResult(id, JsonRpcErrors.INTERNAL_ERROR, "Internal error");
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
                        if (ic != null) {
                            ic.setOutboundStream(null);
                        }
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
                    var context = DefaultMcpContext.stateless(server);
                    context.setOutboundStream(outboundSseStream);
                    try {
                        var result = OutboundSseStreamMessageRouter.withDispatchContext(
                                null, outboundSseStream, () -> handler.handle(context, params));
                        if (result instanceof JsonRpcError error) {
                            return errorResult(id, error.code(), error.message());
                        }
                        return new DispatchResult.Response(encodeResponse(id, result), null);
                    } catch (Exception e) {
                        logger.warn("Stateless handler exception: method={}", method, e);
                        return errorResult(id, JsonRpcErrors.INTERNAL_ERROR, "Internal error");
                    } finally {
                        context.setOutboundStream(null);
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

    private CompletableFuture<DispatchResult> dispatchInitializeAsync(Object id, Object rawParams, McpContext ic) {
        return CompletableFuture.supplyAsync(
                () -> {
                    logger.debug("Client initialize: id={} stateless={}", id, server.isStateless());
                    var handler = server.getHandler("initialize");
                    if (handler == null) {
                        return errorResult(id, JsonRpcErrors.METHOD_NOT_FOUND, "Method not found: initialize");
                    }
                    if (server.isStateless()) {
                        try {
                            var result = handler.handle(DefaultMcpContext.stateless(server), rawParams);
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
                        ic.setSession(session);
                        var result = handler.handle(ic, rawParams);
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
