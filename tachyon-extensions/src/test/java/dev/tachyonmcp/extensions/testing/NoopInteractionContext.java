package dev.tachyonmcp.extensions.testing;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.runtime.Notifications;
import dev.tachyonmcp.runtime.Session;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class NoopInteractionContext implements InteractionContext {

    @Override
    public dev.tachyonmcp.protocol.Protocol getProtocol() {
        return null;
    }

    @Override
    public InteractionContext.Lifecycle getLifecycle() {
        return InteractionContext.Lifecycle.OPERATION;
    }

    @Override
    public Session session() {
        return null;
    }

    @Override
    public boolean isExtensionEnabled(String extensionId) {
        return false;
    }

    @Override
    public Notifications notifications() {
        return null;
    }

    @Override
    public CompletableFuture<String> sendRequest(String method, Object params) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    @Override
    public Map<String, Object> attributes() {
        return Map.of();
    }

    @Override
    public void setAttribute(String name, Object value) {}

    @Override
    public <T> T getAttribute(String name) {
        return null;
    }
}
