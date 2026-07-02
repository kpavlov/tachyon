/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.protocol.Protocol;
import dev.tachyonmcp.protocol.ProtocolMappers;
import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.runtime.DefaultInteractionContext;
import dev.tachyonmcp.runtime.MutableInteractionContext;
import dev.tachyonmcp.runtime.Notifications;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SseEvent;
import dev.tachyonmcp.server.OutboundSseStream;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-request MCP dispatch context decorating the per-channel {@link MutableInteractionContext}
 * with the dispatch surface: server access, notifications, outbound SSE stream, and response
 * mapper. Channel-scoped state (protocol, lifecycle, session, attributes, extensions) delegates to
 * the decorated context; one channel is assumed to carry at most one session.
 *
 * <p>In stateless mode {@link #session()} is {@code null}: notifications are written to the bound
 * outbound stream only, and server-to-client requests fail fast.
 */
public class DefaultMcpContext implements DispatchContext {

    private static final Logger logger = LoggerFactory.getLogger(DefaultMcpContext.class);

    private final MutableInteractionContext channel;
    private final Server server;
    private volatile @Nullable OutboundSseStream outboundStream;

    public DefaultMcpContext(MutableInteractionContext channel, Server server) {
        this.channel = channel;
        this.server = server;
    }

    /** Convenience factory: creates a channel context from protocol, then wraps it with server. */
    public static DispatchContext create(Protocol protocol, Server server) {
        return new DefaultMcpContext(protocol.createInteractionContext(), server);
    }

    /** Context without a session, decorating fresh channel state for the default protocol. */
    public static DispatchContext stateless(Server server) {
        return new DefaultMcpContext(Protocols.versions().get(0).createInteractionContext(), server);
    }

    public static DispatchContext noop() {
        return NoopContext.INSTANCE;
    }

    // === channel-scoped state, delegated ===

    @Override
    public Protocol getProtocol() {
        return channel.getProtocol();
    }

    @Override
    public @Nullable Lifecycle getLifecycle() {
        return channel.getLifecycle();
    }

    @Override
    public void setLifecycle(Lifecycle lifecycle) {
        channel.setLifecycle(lifecycle);
    }

    @Override
    public @Nullable Session session() {
        return channel.session();
    }

    @Override
    public void setSession(@Nullable Session session) {
        channel.setSession(session);
    }

    @Override
    public Map<String, Object> attributes() {
        return channel.attributes();
    }

    @Override
    public void setAttribute(String name, Object value) {
        channel.setAttribute(name, value);
    }

    @Override
    public <T> @Nullable T getAttribute(String name) {
        return channel.getAttribute(name);
    }

    @Override
    public void enableExtension(String extensionId) {
        var s = session();
        if (s != null) {
            s.enableExtension(extensionId);
        } else {
            channel.enableExtension(extensionId);
        }
    }

    @Override
    public boolean isExtensionEnabled(String extensionId) {
        var s = session();
        return s != null ? s.isExtensionEnabled(extensionId) : channel.isExtensionEnabled(extensionId);
    }

    // === request-scoped dispatch surface ===

    @Override
    public Server server() {
        return server;
    }

    @Override
    public void setLoggingLevel(LoggingLevel level) {
        var s = session();
        if (s != null) {
            server.setLoggingLevel(s.id(), level);
        }
    }

    @Override
    @Nullable
    public LoggingLevel getLoggingLevel() {
        var s = session();
        return s != null ? server.getLoggingLevel(s.id()) : null;
    }

    @Override
    public Notifications notifications() {
        return new NotificationsImpl();
    }

    @Override
    public CompletableFuture<String> sendRequest(String method, Object params) {
        var s = session();
        if (s == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Server-to-client requests require a session (stateless mode)"));
        }
        return server.sendRequest(s, method, params, outboundStream);
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
    public ProtocolResponseMapper responseMapper() {
        return getProtocol().responseMapper();
    }

    private class NotificationsImpl implements Notifications {

        @Override
        public void send(String method, Object params) {
            var s = session();
            if (s != null) {
                server.sendNotification(s, method, params, outboundStream());
                return;
            }
            var stream = outboundStream();
            if (stream == null) {
                logger.debug("Dropping notification {}: no session and no outbound stream bound", method);
                return;
            }
            var json = JsonRpcCodec.serializeNotificationAsString(method, JsonRpcCodec.toJsonParams(params));
            stream.start();
            stream.writeEvent(new SseEvent(String.valueOf(server.nextEventId()), "message", json));
        }

        @Override
        public void progress(@Nullable Object progressToken, double progress, double total, String message) {
            if (progressToken == null) return;
            var paramsMap = new LinkedHashMap<String, Object>();
            paramsMap.put("progressToken", progressToken);
            paramsMap.put("progress", progress);
            paramsMap.put("total", total);
            paramsMap.put("message", message);
            send("notifications/progress", paramsMap);
        }

        @Override
        public void info(String logger, Object data) {
            send("notifications/message", Map.of("level", "info", "logger", logger, "data", data));
        }

        @Override
        public void warning(String logger, Object data) {
            send("notifications/message", Map.of("level", "warning", "logger", logger, "data", data));
        }

        @Override
        public void error(String logger, Object data) {
            send("notifications/message", Map.of("level", "error", "logger", logger, "data", data));
        }
    }

    /** Synthetic context for registry-level tests: no server, no protocol, no session. */
    private static final class NoopContext extends DefaultInteractionContext implements DispatchContext {

        static final NoopContext INSTANCE = new NoopContext();

        @SuppressWarnings("DataFlowIssue")
        private NoopContext() {
            super(null);
        }

        @Override
        public Protocol getProtocol() {
            throw new UnsupportedOperationException("No protocol available");
        }

        @Override
        public Lifecycle getLifecycle() {
            throw new UnsupportedOperationException("No lifecycle available");
        }

        @Override
        public Server server() {
            throw new UnsupportedOperationException("No server available");
        }

        @Override
        public void setLoggingLevel(LoggingLevel level) {
            throw new UnsupportedOperationException("No server available");
        }

        @Override
        public @Nullable LoggingLevel getLoggingLevel() {
            return null;
        }

        @Override
        public ProtocolResponseMapper responseMapper() {
            return Objects.requireNonNull(ProtocolMappers.getMapper("mcp", "2025-11-25"));
        }

        @Override
        public @Nullable OutboundSseStream outboundStream() {
            return null;
        }

        @Override
        public void setOutboundStream(@Nullable OutboundSseStream stream) {
            throw new UnsupportedOperationException("No outbound stream available");
        }
    }
}
