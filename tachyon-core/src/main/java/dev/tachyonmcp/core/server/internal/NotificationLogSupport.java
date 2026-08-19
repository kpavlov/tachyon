/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.internal;

/**
 * Shared {@code notifications/message} method name for the two independent notification senders —
 * {@code DefaultDispatchContext.NotificationsImpl} (per-request) and {@code
 * DefaultTachyonServer.broadcastLog} (server-wide) — which live in different packages and gate
 * emission with their own, unrelated {@code shouldEmit} logic. Each builds its wire params via
 * {@link dev.tachyonmcp.core.protocol.ProtocolResponseMapper#loggingMessageParams}.
 */
public final class NotificationLogSupport {

    /** The MCP method for structured log notifications. */
    public static final String LOG_METHOD = "notifications/message";

    private NotificationLogSupport() {}
}
