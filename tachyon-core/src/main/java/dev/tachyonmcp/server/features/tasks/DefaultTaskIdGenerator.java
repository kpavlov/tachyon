/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.protocol.api.annotations.InternalApi;
import dev.tachyonmcp.protocol.api.server.features.tasks.TaskIdGenerator;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@InternalApi
public class DefaultTaskIdGenerator implements TaskIdGenerator {
    public static final TaskIdGenerator INSTANCE = new DefaultTaskIdGenerator();

    private DefaultTaskIdGenerator() {
        // noop
    }

    @Override
    public String generateTaskId(@Nullable Map<String, Object> meta, @Nullable String sessionId) {
        return "tid_" + UUID.randomUUID().toString().replace("-", "");
    }
}
