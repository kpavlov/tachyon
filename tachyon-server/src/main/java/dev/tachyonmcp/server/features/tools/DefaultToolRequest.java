/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.Cancellation;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

record DefaultToolRequest(
        String name,
        @Nullable Map<String, JsonNode> arguments,
        @Nullable Map<String, JsonNode> meta,
        @Nullable Object progressToken,
        @Nullable Cancellation cancellation)
        implements ToolRequest {

    DefaultToolRequest {
        Objects.requireNonNull(name, "name");
    }
}
