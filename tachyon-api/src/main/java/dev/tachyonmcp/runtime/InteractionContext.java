/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.runtime;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Handler-facing view of the per-channel interaction: the current lifecycle phase, protocol
 * version, optional session identifier, and the collaboration channels a tool/resource/prompt
 * handler legitimately needs ({@link #notifications()} and {@link #sendRequest(String, Object)}).
 *
 * <p>This interface deliberately exposes <em>no</em> mutators — handlers may read state and use the
 * {@link #attributes() attribute} scratch space, but lifecycle and session mutation live on the
 * internal channel context handed to extension and dispatch code only.
 */
public interface InteractionContext {
    /**
     * Lifecycle phases of an MCP interaction.
     */
    enum Lifecycle {
        /** Server is waiting for or processing the initialisation handshake. */
        INITIALIZATION,
        /** Normal operation after successful initialisation and session establishment. */
        OPERATION,
        /** The interaction is shutting down. */
        SHUTDOWN
    }

    /** Returns the protocol version negotiated during initialisation. */
    String protocolVersion();

    /** Returns the current lifecycle phase, or {@code null} before initialisation. */
    @Nullable
    Lifecycle lifecycle();

    /** Returns the session identifier, or {@code null} in stateless mode. */
    @Nullable
    String sessionId();

    /** Returns {@code true} if the given extension is active for this interaction. */
    boolean isExtensionEnabled(String extensionId);

    /** Returns the notification sender bound to this interaction. */
    ContextNotifications notifications();

    /**
     * Sends a request to the client and returns a future that completes with the raw JSON response.
     * Used for sampling/elicitation roundtrips.
     */
    CompletableFuture<String> sendRequest(String method, Object params);

    /** Returns an unmodifiable view of the attribute map for this interaction context. */
    Map<String, Object> attributes();

    /** Sets a named attribute on this context. */
    <T> void setAttribute(String name, T value);

    /** Gets a named attribute, or {@code null} if not set. */
    <T> @Nullable T getAttribute(String name);
}
