/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.internal;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SseEvent;
import dev.tachyonmcp.server.OutboundSseStream;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.ServerCapabilities;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.domain.RequestId;
import dev.tachyonmcp.server.features.tasks.TaskRegistry;
import dev.tachyonmcp.server.session.SessionEvent;
import dev.tachyonmcp.server.session.SessionIdGenerator;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.jspecify.annotations.Nullable;

/**
 * Engine SPI for MCP server internals. Extends the user-facing {@link TachyonServer} with the full
 * set of methods needed by transport, dispatch, registries, and session management.
 *
 * <p><strong>Not a stability contract.</strong> This interface lives in an {@code internal}
 * package for a reason: it may change without notice across minor versions. Only code that ships
 * inside the {@code tachyon-server} jar — transport handlers, dispatch plumbing, registry
 * implementations — should depend on it. External code that reaches for {@code ServerEngine} is
 * opting into a breakage-prone dependency.
 */
// ponytail: internal pkg not JPMS-sealed; module split later if a user pins ServerEngine.
@InternalApi
public interface ServerEngine extends TachyonServer {

    /** Returns the protocol response mapper for the default MCP version. */
    ProtocolResponseMapper responseMapper();

    /** Returns {@code true} when running in stateless mode (no session persistence). */
    boolean isStateless();

    /** Returns the configured session id generator. */
    SessionIdGenerator sessionIdGenerator();

    /** Sets the logging level for a session. */
    void setLoggingLevel(String sessionId, LoggingLevel level);

    /** Returns the logging level for a session, or {@code null} if not set. */
    @Nullable
    LoggingLevel getLoggingLevel(String sessionId);

    /** Resolves effective capabilities based on configuration and registered features. */
    ServerCapabilities resolveCapabilities();

    /** Sends a notification to all active sessions. */
    void broadcastNotification(String method, Object params);

    /** Registers a method handler keyed by its own method name. */
    void registerHandler(RpcMethodHandler handler);

    /** Registers a method handler with an explicit method name. */
    void registerHandler(String method, RpcMethodHandler handler);

    /** Returns the handler for a method, or {@code null} if not registered. */
    @Nullable
    RpcMethodHandler getHandler(String method);

    /** Returns the extension ID that owns the given method, or {@code null} if none. */
    @Nullable
    String extensionForMethod(String method);

    /** Returns {@code true} if the given extension requires the meta envelope for its methods. */
    boolean extensionRequiresMeta(String extensionId);

    /** Creates and registers a new session with the given ID. */
    Session createSession(String sessionId);

    /** Returns the session with the given ID, if present. */
    Optional<Session> getSession(String sessionId);

    /** Removes and closes the session with the given ID. */
    void removeSession(String sessionId);

    /** Sends a notification to the given session. */
    void sendNotification(Session session, String method, Object params);

    /** Sends a notification to the given session, optionally via a bound outbound SSE stream. */
    void sendNotification(Session session, String method, @Nullable Object params, @Nullable OutboundSseStream stream);

    /** Returns the executor used for handler dispatch. */
    ExecutorService executor();

    TaskRegistry tasksRegistry();

    /** Sends a request to the client and returns a future that completes with the response. */
    CompletableFuture<String> sendRequest(Session session, String method, Object params);

    /** Sends a request to the client, optionally via a bound outbound SSE stream, and returns a future. */
    CompletableFuture<String> sendRequest(
            Session session, String method, Object params, @Nullable OutboundSseStream stream);

    /** Completes a pending client request with the given result JSON. {@code null} requestId is a no-op. */
    boolean completePendingRequest(@Nullable RequestId requestId, String resultJson);

    /** Fails a pending client request with the given error message. {@code null} requestId is a no-op. */
    boolean failPendingRequest(@Nullable RequestId requestId, String message);

    /** Registers a pending request with a timeout. */
    void registerPendingRequest(RequestId requestId, CompletableFuture<String> future);

    /** Appends an event to the session log. */
    void appendEvent(SessionEvent event);

    /** Replays session events after the given sequence number. */
    List<SessionEvent> replay(String sessionId, long lastSeq);

    /** Returns and increments the event ID counter. */
    long nextEventId();

    // ──────────────────────────────────────────────────────────────
    // Static helpers
    // ──────────────────────────────────────────────────────────────

    /** Formats an SSE wire event id: the global counter value, suffixed with {@code #<streamKey>}. */
    static String wireEventId(long sseEventId, @Nullable String streamKey) {
        return streamKey == null ? String.valueOf(sseEventId) : sseEventId + "#" + streamKey;
    }

    /** Converts a session event to an SSE event, or returns {@code null} for non-transport events. */
    static @Nullable SseEvent toSseEvent(SessionEvent event) {
        return switch (event) {
            case SessionEvent.ResponseEvent r ->
                new SseEvent(wireEventId(r.sseEventId(), r.streamKey()), "message", r.resultJson());
            case SessionEvent.NotificationEvent n -> {
                var json = JsonRpcCodec.serializeNotificationAsString(n.method(), n.paramsJson());
                yield new SseEvent(wireEventId(n.sseEventId(), n.streamKey()), "message", json);
            }
            case SessionEvent.OutboundRequestEvent o -> {
                var json = JsonRpcCodec.serializeRequestAsString(o.requestId(), o.method(), o.paramsJson());
                yield new SseEvent(wireEventId(o.sseEventId(), o.streamKey()), "message", json);
            }
            case SessionEvent.RequestEvent ignored -> null;
            case SessionEvent.CancelEvent ignored -> null;
        };
    }
}
