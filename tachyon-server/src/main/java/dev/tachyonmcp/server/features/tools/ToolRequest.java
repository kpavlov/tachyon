/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.Cancellation;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public record ToolRequest(
        String name,
        @Nullable Map<String, JsonNode> arguments,
        @Nullable Map<String, JsonNode> meta,
        @Nullable Object progressToken,
        @Nullable Cancellation cancellation) {

    public ToolRequest(String name, @Nullable Map<String, JsonNode> arguments, @Nullable Map<String, JsonNode> meta) {
        this(name, arguments, meta, null, null);
    }

    public ToolRequest {
        if (name == null) throw new NullPointerException("name");
    }
}
