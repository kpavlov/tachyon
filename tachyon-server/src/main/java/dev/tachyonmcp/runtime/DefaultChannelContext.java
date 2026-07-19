/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.Protocol;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

@InternalApi
public class DefaultChannelContext implements ChannelContext {

    private final Protocol protocol;

    private final Map<String, Object> attributes = new ConcurrentHashMap<>(3);
    private final Set<String> enabledExtensions = ConcurrentHashMap.newKeySet();

    private volatile Lifecycle lifecycle = Lifecycle.INITIALIZATION;

    private final AtomicReference<@Nullable Session> sessionHolder = new AtomicReference<>();

    public DefaultChannelContext(Protocol protocol) {
        this.protocol = protocol;
    }

    @Override
    public Protocol protocol() {
        return protocol;
    }

    @Override
    public Lifecycle lifecycle() {
        return lifecycle;
    }

    @Override
    public void setLifecycle(Lifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    @Nullable
    public Session session() {
        return sessionHolder.get();
    }

    @Override
    public void setSession(@Nullable Session session) {
        this.sessionHolder.set(session);
    }

    @Override
    public void enableExtension(String extensionId) {
        enabledExtensions.add(extensionId);
    }

    @Override
    public boolean isExtensionEnabled(String extensionId) {
        return enabledExtensions.contains(extensionId);
    }

    @Override
    public ContextNotifications notifications() {
        throw new UnsupportedOperationException("notifications are not supported in this context");
    }

    @Override
    public CompletableFuture<String> sendRequest(String method, Object params) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("sendRequest is not supported in this context"));
    }

    @Override
    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public <T> void setAttribute(String name, T value) {
        attributes.put(name, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getAttribute(String name) {
        return (T) attributes.get(name);
    }
}
