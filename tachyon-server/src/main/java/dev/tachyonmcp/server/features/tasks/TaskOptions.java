/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public record TaskOptions(@Nullable Duration ttl, @Nullable Map<String, JsonNode> meta) {

    public TaskOptions {
        meta = meta != null ? Map.copyOf(meta) : null;
    }
}
