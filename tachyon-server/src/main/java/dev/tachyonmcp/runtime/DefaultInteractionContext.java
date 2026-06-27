/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

import dev.tachyonmcp.protocol.Protocol;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Per-channel mutable context tracking the interaction lifecycle phase, the bound
 * {@link Session}, and protocol version.
 */
public class DefaultInteractionContext<S extends Session> implements InteractionContext<S> {

    private final Protocol protocol;

    private final Map<String, Object> attributes = new ConcurrentHashMap<>(3);
    private final Set<String> enabledExtensions = ConcurrentHashMap.newKeySet();

    private volatile Lifecycle lifecycle = Lifecycle.INITIALIZATION;

    private final AtomicReference<@Nullable S> sessionHolder = new AtomicReference<>();

    public DefaultInteractionContext(Protocol protocol) {
        this.protocol = protocol;
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    @Override
    public void setLifecycle(Lifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    @Nullable
    public S session() {
        return sessionHolder.get();
    }

    @Override
    public void setSession(S session) {
        if (!this.sessionHolder.compareAndSet(null, session)) {
            throw new IllegalStateException("Session already set");
        }
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
    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getAttribute(String name) {
        return (T) attributes.get(name);
    }
}
