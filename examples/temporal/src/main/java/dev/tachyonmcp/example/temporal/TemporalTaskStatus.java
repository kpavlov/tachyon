/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.example.temporal;

import dev.tachyonmcp.api.server.features.tasks.TaskState;
import java.time.Instant;
import java.util.Map;

/** Application-owned workflow status queried by the Tachyon adapter. */
public record TemporalTaskStatus(
        TaskState state,
        String message,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> result,
        long revision) {}
