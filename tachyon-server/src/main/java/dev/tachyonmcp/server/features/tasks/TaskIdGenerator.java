/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public interface TaskIdGenerator {
    String generateTaskId(@Nullable Map<String, JsonNode> meta, @Nullable String sessionId);
}
