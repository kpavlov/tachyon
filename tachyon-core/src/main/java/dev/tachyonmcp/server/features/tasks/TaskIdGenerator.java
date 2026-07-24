/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface TaskIdGenerator {
    String generateTaskId(@Nullable Map<String, Object> meta, @Nullable String sessionId);
}
