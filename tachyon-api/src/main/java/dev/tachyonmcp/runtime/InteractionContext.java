/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.runtime;

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

    /**
     * Returns the value stored under {@code key}, or empty if never set.
     *
     * <p>Scoped to the underlying channel/session, not the current request — a value set on one
     * request is visible to later requests on the same connection. Backed by a concurrent map:
     * safe to call from multiple handler threads without external synchronization.
     */
    <T> Optional<T> get(AttributeKey<T> key);

    /**
     * Stores {@code value} under {@code key}, visible to later {@link #get(AttributeKey)} calls —
     * see {@link #get(AttributeKey)} for scope and thread-safety.
     */
    <T> void set(AttributeKey<T> key, T value);
}
