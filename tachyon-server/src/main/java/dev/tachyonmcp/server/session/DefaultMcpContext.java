/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.protocol.ProtocolMappers;
import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.Notifications;
import dev.tachyonmcp.server.ServerContext;
import dev.tachyonmcp.server.domain.LoggingLevel;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

public class DefaultMcpContext implements McpContext {

    private static final McpContext NOOP_CONTEXT = new DefaultMcpContext() {
        @Override
        public ServerContext server() {
            throw new UnsupportedOperationException("No server context available");
        }

        @Override
        public Notifications notifications() {
            throw new UnsupportedOperationException("No notifications available");
        }

        @Override
        public @Nullable McpSession session() {
            return null;
        }

        @Override
        public @Nullable String getProtocol() {
            throw new UnsupportedOperationException("No protocol available");
        }

        @Override
        public @Nullable Lifecycle getLifecycle() {
            throw new UnsupportedOperationException("No lifecycle available");
        }

        @Override
        public ProtocolResponseMapper responseMapper() {
            return Objects.requireNonNull(ProtocolMappers.getMapper("mcp", "2025-11-25"));
        }
    };

    private final ServerContextImpl server;
    private final NotificationsImpl notifications;
    private final McpSession session;
    private final @Nullable InteractionContext<McpSession> interactionContext;

    public static McpContext noop() {
        return NOOP_CONTEXT;
    }

    private static final String STATELESS_SESSION_ID = "stateless";

    public static McpContext stateless(McpServer server) {
        return new DefaultMcpContext(new McpSession(STATELESS_SESSION_ID, SseConnection.NOOP), server);
    }

    protected DefaultMcpContext() {
        this.server = null;
        this.notifications = null;
        this.session = null;
        this.interactionContext = null;
    }

    public DefaultMcpContext(McpSession session, McpServer server) {
        this(session, server, null);
    }

    public DefaultMcpContext(McpSession session, McpServer server, @Nullable InteractionContext<McpSession> ic) {
        this.session = session;
        this.interactionContext = ic;
        this.server = new ServerContextImpl(session, server);
        this.notifications = new NotificationsImpl(session, server);
    }

    @Override
    public ServerContext server() {
        return server;
    }

    @Override
    public Notifications notifications() {
        return notifications;
    }

    @Override
    public @Nullable McpSession session() {
        return session;
    }

    @Override
    public void enableExtension(String extensionId) {
        if (interactionContext != null) interactionContext.enableExtension(extensionId);
        if (session != null) session.enableExtension(extensionId);
    }

    @Override
    public boolean isExtensionEnabled(String extensionId) {
        if (interactionContext != null) return interactionContext.isExtensionEnabled(extensionId);
        return session != null && session.isExtensionEnabled(extensionId);
    }

    @Override
    public @Nullable String getProtocol() {
        return interactionContext != null ? interactionContext.getProtocol() : null;
    }

    @Override
    public @Nullable String getProtocolVersion() {
        return session != null ? session.protocolVersion() : null;
    }

    @Override
    public void setProtocolVersion(@Nullable String protocolVersion) {
        if (session != null) {
            session.protocolVersion(protocolVersion);
        }
    }

    @Override
    public ProtocolResponseMapper responseMapper() {
        return server.mcpServer().responseMapper();
    }

    @Override
    public @Nullable Lifecycle getLifecycle() {
        return interactionContext != null ? interactionContext.getLifecycle() : null;
    }

    @Override
    public void setLifecycle(Lifecycle lifecycle) {
        if (interactionContext != null) interactionContext.setLifecycle(lifecycle);
    }

    @Override
    public void setSession(McpSession session) {
        if (interactionContext != null) interactionContext.setSession(session);
    }

    @Override
    public Map<String, Object> attributes() {
        return interactionContext != null ? interactionContext.attributes() : Map.of();
    }

    @Override
    public void setAttribute(String name, Object value) {
        if (interactionContext != null) interactionContext.setAttribute(name, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getAttribute(String name) {
        if (interactionContext != null) {
            return interactionContext.getAttribute(name);
        }
        return null;
    }

    private record ServerContextImpl(McpSession session, McpServer server) implements ServerContext {

        @Override
        public String sessionId() {
            return session.id();
        }

        @Override
        @Nullable
        public String protocolVersion() {
            return session.protocolVersion();
        }

        @Override
        public void protocolVersion(String version) {
            session.protocolVersion(version);
        }

        @Override
        public void setLoggingLevel(LoggingLevel level) {
            server.setLoggingLevel(session.id(), level);
        }

        @Override
        @Nullable
        public LoggingLevel getLoggingLevel() {
            return server.getLoggingLevel(session.id());
        }

        @Override
        public CompletableFuture<String> sendRequest(String method, Object params) {
            return server.sendRequest(session, method, params);
        }

        @Override
        public McpSession session() {
            return session;
        }

        @Override
        public McpServer mcpServer() {
            return server;
        }
    }

    private record NotificationsImpl(McpSession session, McpServer server) implements Notifications {

        @Override
        public void send(String method, Object params) {
            server.sendNotification(session, method, params);
        }

        @Override
        public void progress(@Nullable Object progressToken, double progress, double total, String message) {
            if (progressToken == null) return;
            var paramsMap = new LinkedHashMap<String, Object>();
            paramsMap.put("progressToken", progressToken);
            paramsMap.put("progress", progress);
            paramsMap.put("total", total);
            paramsMap.put("message", message);
            server.sendNotification(session, "notifications/progress", paramsMap);
        }

        private void logAtConfiguredLevel(String logger, Object data) {
            var level = server.getLoggingLevel(session.id());
            if (level == null) return;
            server.log(session, level, logger, data);
        }

        @Override
        public void info(String logger, Object data) {
            logAtConfiguredLevel(logger, data);
        }

        @Override
        public void warning(String logger, Object data) {
            logAtConfiguredLevel(logger, data);
        }

        @Override
        public void error(String logger, Object data) {
            logAtConfiguredLevel(logger, data);
        }
    }
}
