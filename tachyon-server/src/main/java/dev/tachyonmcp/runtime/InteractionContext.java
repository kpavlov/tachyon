/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

import dev.tachyonmcp.protocol.Protocol;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Handler-facing view of the per-channel interaction: the current lifecycle phase, the bound
 * {@link Session}, protocol version, and the collaboration channels a tool/resource/prompt handler
 * legitimately needs ({@link #notifications()} and {@link #sendRequest(String, Object)}).
 *
 * <p>This interface deliberately exposes <em>no</em> mutators — handlers may read state and use the
 * {@link #attributes() attribute} scratch space, but lifecycle/session/extension mutation lives on
 * {@link MutableInteractionContext}, handed to extension and dispatch code only.
 */
public interface InteractionContext {
    enum Lifecycle {
        INITIALIZATION,
        OPERATION,
        SHUTDOWN
    }

    Protocol getProtocol();

    default String getProtocolVersion() {
        return getProtocol().versionString();
    }

    @Nullable
    Lifecycle getLifecycle();

    /**
     * Optional Session. Session is <code>null</code> in stateless mode.
     */
    @Nullable
    Session session();

    boolean isExtensionEnabled(String extensionId);

    /** Returns the notification sender bound to this interaction. */
    Notifications notifications();

    /**
     * Sends a request to the client and returns a future that completes with the raw JSON response.
     * Used for sampling/elicitation roundtrips.
     */
    CompletableFuture<String> sendRequest(String method, Object params);

    Map<String, Object> attributes();

    void setAttribute(String name, Object value);

    <T> @Nullable T getAttribute(String name);
}
