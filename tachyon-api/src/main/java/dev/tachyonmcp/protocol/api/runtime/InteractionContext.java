/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.protocol.api.runtime;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Handler-facing view of the per-channel interaction: the current lifecycle phase, protocol
 * version, optional session identifier, and the collaboration channels a tool/resource/prompt
 * handler legitimately needs ({@link #notifications()} and {@link #sendRequest(String, Object)}).
 *
 * <p>This interface deliberately exposes <em>no</em> mutators — handlers may read state and use the
 * {@link #get(AttributeKey) attribute} scratch space, but lifecycle and session mutation live on
 * the internal channel context handed to extension and dispatch code only.
 *
 * <p>The attribute scratch space ({@link #get(AttributeKey)}/{@link #set(AttributeKey, Object)})
 * is keyed by {@link AttributeKey}, not a {@code String}, so unrelated handlers can never collide
 * on a shared name — see {@link AttributeKey} for why.
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

    /**
     * Returns the protocol version negotiated during initialisation.
     *
     * @return the negotiated protocol version
     */
    String protocolVersion();

    /**
     * Returns the current lifecycle phase, or {@code null} before initialisation.
     *
     * @return the lifecycle phase, or {@code null}
     */
    @Nullable
    Lifecycle lifecycle();

    /**
     * Returns the session identifier, or {@code null} in stateless mode.
     *
     * @return the session ID, or {@code null}
     */
    @Nullable
    String sessionId();

    /**
     * Returns {@code true} if the given extension is active for this interaction.
     *
     * @param extensionId the extension identifier
     * @return {@code true} if the extension is active
     */
    boolean isExtensionEnabled(String extensionId);

    /**
     * Returns the notification sender bound to this interaction.
     *
     * @return the notification sender
     */
    ContextNotifications notifications();

    /**
     * Sends a request to the client and returns a future that completes with the raw JSON response.
     * Used for sampling/elicitation roundtrips.
     *
     * @param method the request method name
     * @param params the request parameters
     * @return a future that completes with the raw JSON response
     */
    CompletableFuture<String> sendRequest(String method, Object params);

    /**
     * Returns the value stored under {@code key}, or empty if never set.
     *
     * <p>Scoped to the underlying channel/session, not the current request — a value set on one
     * request is visible to later requests on the same connection. Backed by a concurrent map:
     * safe to call from multiple handler threads without external synchronization.
     *
     * @param <T> the type of value
     * @param key the attribute key
     * @return the value, or empty if not set
     */
    <T> Optional<T> get(AttributeKey<T> key);

    /**
     * Stores {@code value} under {@code key}, visible to later {@link #get(AttributeKey)} calls —
     * see {@link #get(AttributeKey)} for scope and thread-safety.
     *
     * @param <T>   the type of value
     * @param key   the attribute key
     * @param value the value to store
     */
    <T> void set(AttributeKey<T> key, T value);
}
