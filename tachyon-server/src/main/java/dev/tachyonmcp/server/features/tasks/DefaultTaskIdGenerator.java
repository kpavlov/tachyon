/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.annotations.InternalApi;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@InternalApi
public class DefaultTaskIdGenerator implements TaskIdGenerator {
    public static final TaskIdGenerator INSTANCE = new DefaultTaskIdGenerator();

    private DefaultTaskIdGenerator() {
        // noop
    }

    @Override
    public String generateTaskId(@Nullable Map<String, JsonNode> meta, @Nullable String sessionId) {
        return "tid_" + UUID.randomUUID().toString().replace("-", "");
    }
}
