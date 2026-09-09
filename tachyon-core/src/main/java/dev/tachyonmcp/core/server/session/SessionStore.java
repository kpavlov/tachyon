/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.session;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import java.time.Instant;
import java.util.Optional;

/**
 * Persistence boundary for immutable, transport-free MCP session snapshots.
 *
 * <p>Methods execute synchronously and may perform I/O. Tachyon invokes them outside transport
 * event-loop threads. Implementations must be thread-safe.
 */
@ExperimentalApi(since = "1.0.0-beta.26")
public interface SessionStore extends AutoCloseable {

    /** Creates a new generation, atomically replacing the current generation for its session ID. */
    SessionSnapshot create(SessionKey key, Instant expiresAt);

    /** Finds the current snapshot for a session ID. */
    Optional<SessionSnapshot> find(String sessionId);

    /** Replaces the current snapshot when it exactly equals {@code expected}. */
    boolean compareAndSet(SessionSnapshot expected, SessionSnapshot updated);

    /**
     * Extends expiry and increments the revision once when the current snapshot still belongs to
     * {@code key}.
     */
    boolean touch(SessionKey key, Instant expiresAt);

    /** Removes the current snapshot when its generation still equals {@code key}. */
    boolean terminate(SessionKey key);
}
