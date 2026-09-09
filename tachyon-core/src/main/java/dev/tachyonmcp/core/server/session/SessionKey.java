/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.session;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import java.util.Objects;

/**
 * Identifies one generation of an MCP session.
 *
 * @param sessionId stable client-visible session identifier
 * @param generationId opaque identifier that fences stale lifecycle work
 */
@ExperimentalApi(since = "1.0.0-beta.26")
public record SessionKey(String sessionId, String generationId) {

    /** Validates the session and generation identifiers. */
    public SessionKey {
        if (Objects.requireNonNull(sessionId, "sessionId").isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be blank");
        }
        if (Objects.requireNonNull(generationId, "generationId").isBlank()) {
            throw new IllegalArgumentException("generationId cannot be blank");
        }
    }
}
