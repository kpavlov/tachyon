/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.protocol.Protocol;
import dev.tachyonmcp.protocol.ProtocolMappers;
import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.runtime.DefaultInteractionContext;
import dev.tachyonmcp.runtime.Notifications;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SseConnection;
import dev.tachyonmcp.server.OutboundSseStream;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.ServerContext;
import dev.tachyonmcp.server.domain.LoggingLevel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

public class DefaultMcpContext extends DefaultInteractionContext implements DispatchContext {

    private static final DispatchContext NOOP_CONTEXT = new DefaultMcpContext(null, null) {
        @Override
        public ServerContext server() {
            throw new UnsupportedOperationException("No server context available");
        }

        @Override
        public Notifications notifications() {
            throw new UnsupportedOperationException("No notifications available");
        }

        @Override
        public @Nullable Session session() {
            return null;
        }

        @Override
        public @Nullable Protocol getProtocol() {
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

    private static final String STATELESS_SESSION_ID = "stateless";

    private final Server server;
    private volatile @Nullable OutboundSseStream outboundStream;

    public static DispatchContext noop() {
        return NOOP_CONTEXT;
    }

    public static DispatchContext stateless(Server server) {
        var protocol = Protocols.versions().get(0);
        var ctx = new DefaultMcpContext(protocol, server);
        ctx.setSession(new Session(STATELESS_SESSION_ID, SseConnection.NOOP));
        return ctx;
    }

    public DefaultMcpContext(Protocol protocol, Server server) {
        super(protocol);
        this.server = server;
    }

    @Override
    public ServerContext server() {
        return new ServerCtx();
    }

    @Override
    public Notifications notifications() {
        return new NotificationsImpl();
    }

    @Override
    public CompletableFuture<String> sendRequest(String method, Object params) {
        return server.sendRequest(session(), method, params, outboundStream);
    }

    @Override
    public @Nullable Session session() {
        return super.session();
    }

    @Override
    public @Nullable OutboundSseStream outboundStream() {
        return outboundStream;
    }

    @Override
    public void setOutboundStream(@Nullable OutboundSseStream stream) {
        this.outboundStream = stream;
    }

    @Override
    public void enableExtension(String extensionId) {
        var s = session();
        if (s != null) s.enableExtension(extensionId);
    }

    @Override
    public boolean isExtensionEnabled(String extensionId) {
        var s = session();
        return s != null && s.isExtensionEnabled(extensionId);
    }

    @Override
    public ProtocolResponseMapper responseMapper() {
        return server.responseMapper();
    }

    private class ServerCtx implements ServerContext {

        @Override
        public String sessionId() {
            return session().id();
        }

        @Override
        public void setLoggingLevel(LoggingLevel level) {
            server.setLoggingLevel(session().id(), level);
        }

        @Override
        @Nullable
        public LoggingLevel getLoggingLevel() {
            return server.getLoggingLevel(session().id());
        }

        @Override
        @Nullable
        public Session session() {
            return DefaultMcpContext.this.session();
        }

        @Override
        public Server mcpServer() {
            return server;
        }
    }

    private class NotificationsImpl implements Notifications {

        @Override
        public void send(String method, Object params) {
            server.sendNotification(session(), method, params, DefaultMcpContext.this.outboundStream());
        }

        @Override
        public void progress(@Nullable Object progressToken, double progress, double total, String message) {
            if (progressToken == null) return;
            var paramsMap = new LinkedHashMap<String, Object>();
            paramsMap.put("progressToken", progressToken);
            paramsMap.put("progress", progress);
            paramsMap.put("total", total);
            paramsMap.put("message", message);
            server.sendNotification(
                    session(), "notifications/progress", paramsMap, DefaultMcpContext.this.outboundStream());
        }

        @Override
        public void info(String logger, Object data) {
            server.sendNotification(
                    session(),
                    "notifications/message",
                    Map.of("level", "info", "logger", logger, "data", data),
                    DefaultMcpContext.this.outboundStream());
        }

        @Override
        public void warning(String logger, Object data) {
            server.sendNotification(
                    session(),
                    "notifications/message",
                    Map.of("level", "warning", "logger", logger, "data", data),
                    DefaultMcpContext.this.outboundStream());
        }

        @Override
        public void error(String logger, Object data) {
            server.sendNotification(
                    session(),
                    "notifications/message",
                    Map.of("level", "error", "logger", logger, "data", data),
                    DefaultMcpContext.this.outboundStream());
        }
    }
}
