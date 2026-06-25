/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Per-channel mutable context tracking the interaction lifecycle phase, the bound
 * {@link Session}, and protocol version.
 */
public interface InteractionContext<S extends Session> {
    enum Lifecycle {
        INITIALIZATION,
        OPERATION,
        SHUTDOWN
    }

    @Nullable
    String getProtocol();

    @Nullable
    String getProtocolVersion();

    void setProtocolVersion(@Nullable String protocolVersion);

    @Nullable
    Lifecycle getLifecycle();

    void setLifecycle(Lifecycle lifecycle);

    /**
     * Optional Session. Session is <code>null</code> in stateless mode.
     */
    @Nullable
    S session();

    void setSession(@Nullable S session);

    void enableExtension(String extensionId);

    boolean isExtensionEnabled(String extensionId);

    Map<String, Object> attributes();

    void setAttribute(String name, Object value);

    <T> @Nullable T getAttribute(String name);
}
