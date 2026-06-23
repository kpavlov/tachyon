/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.Cancellation;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public interface ToolRequest {

    String name();

    @Nullable
    Map<String, JsonNode> arguments();

    @Nullable
    Map<String, JsonNode> meta();

    @Nullable
    Object progressToken();

    @Nullable
    Cancellation cancellation();

    static ToolRequest of(
            String name,
            @Nullable Map<String, JsonNode> arguments,
            @Nullable Map<String, JsonNode> meta,
            @Nullable Object progressToken,
            @Nullable Cancellation cancellation) {
        return new DefaultToolRequest(name, arguments, meta, progressToken, cancellation);
    }

    static ToolRequest of(
            String name, @Nullable Map<String, JsonNode> arguments, @Nullable Map<String, JsonNode> meta) {
        return new DefaultToolRequest(name, arguments, meta, null, null);
    }
}
