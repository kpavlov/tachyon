/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.session;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.Protocol;
import dev.tachyonmcp.protocol.ProtocolMappers;
import dev.tachyonmcp.protocol.ProtocolResponseMapper;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.McpProtocol;
import dev.tachyonmcp.runtime.Notifications;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.server.OutboundSseStream;
import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.internal.ServerEngine;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

@InternalApi
public class NoopInteractionContext implements DispatchContext {

    public static final NoopInteractionContext INSTANCE = new NoopInteractionContext();

    @Override
    public Protocol protocol() {
        throw new UnsupportedOperationException("No interaction context available");
    }

    @Override
    public @Nullable Lifecycle lifecycle() {
        return Lifecycle.OPERATION;
    }

    @Override
    public void setLifecycle(Lifecycle lifecycle) {}

    @Override
    public @Nullable Session session() {
        return null;
    }

    @Override
    public void setSession(@Nullable Session session) {}

    @Override
    public Map<String, Object> attributes() {
        return Map.of();
    }

    @Override
    public <T> void setAttribute(String name, T value) {}

    @Override
    public <T> @Nullable T getAttribute(String name) {
        return null;
    }

    @Override
    public void enableExtension(String extensionId) {}

    @Override
    public boolean isExtensionEnabled(String extensionId) {
        return false;
    }

    @Override
    public Notifications notifications() {
        throw new UnsupportedOperationException("No interaction context available");
    }

    @Override
    public CompletableFuture<String> sendRequest(String method, Object params) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("sendRequest"));
    }

    @Override
    public ServerEngine engine() {
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
        return Objects.requireNonNull(ProtocolMappers.getMapper("mcp", McpProtocol.VERSION));
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
