/* Copyright (c) 2026 Konstantin Pavlov and contributors. */
package dev.tachyonmcp.server.features.tasks;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Strategy for generating task identifiers.
 */
@FunctionalInterface
public interface TaskIdGenerator {
    /**
     * Generates a task identifier.
     *
     * @param meta      the request metadata, or {@code null}
     * @param sessionId the current session identifier, or {@code null}
     * @return a unique task identifier
     */
    String generateTaskId(@Nullable Map<String, Object> meta, @Nullable String sessionId);
}
