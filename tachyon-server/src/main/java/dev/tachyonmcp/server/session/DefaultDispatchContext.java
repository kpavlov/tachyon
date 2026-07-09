/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.Protocol;
import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.runtime.ChannelContext;
import dev.tachyonmcp.runtime.Notifications;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SseEvent;
import dev.tachyonmcp.server.OutboundSseStream;
import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InternalApi
public class DefaultDispatchContext implements DispatchContext {

    private static final Logger logger = LoggerFactory.getLogger(DefaultDispatchContext.class);

    private final ChannelContext channel;
    private final ServerEngine server;
    private volatile @Nullable OutboundSseStream outboundStream;

    public DefaultDispatchContext(ChannelContext channel, ServerEngine server) {
        this.channel = channel;
        this.server = server;
    }

    public static DispatchContext create(Protocol protocol, ServerEngine server) {
        return new DefaultDispatchContext(protocol.createInteractionContext(), server);
    }

    public static DispatchContext stateless(ServerEngine server) {
        return new DefaultDispatchContext(Protocols.list().getFirst().createInteractionContext(), server);
    }

    public static DispatchContext noop() {
        return NoopInteractionContext.INSTANCE;
    }

    // === channel-scoped state, delegated ===

    @Override
    public Protocol protocol() {
        return channel.protocol();
    }

    @Override
    public @Nullable Lifecycle lifecycle() {
        return channel.lifecycle();
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
    public <T> void setAttribute(String name, T value) {
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
    public ServerEngine engine() {
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
        return protocol().responseMapper();
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
            stream.writeEvent(
                    new SseEvent(ServerEngine.wireEventId(server.nextEventId(), stream.streamKey()), "message", json));
        }

        @Override
        public void progress(Object progressToken, double progress, double total, String message) {
            Objects.requireNonNull(progressToken, "Progress token is required");
            var paramsMap = Map.of(
                    "progressToken", progressToken,
                    "progress", progress,
                    "total", total,
                    "message", message);
            send("notifications/progress", paramsMap);
        }

        @Override
        public void comment(@Nullable String message) {
            var stream = outboundStream();
            if (stream == null) {
                logger.debug("Dropping SSE comment: no outbound stream bound");
                return;
            }
            stream.comment(message);
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
}
