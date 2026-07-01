/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.Cancellation;
import dev.tachyonmcp.server.domain.HasMeta;
import java.util.Collections;
import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface ToolRequest extends HasMeta {

    String name();

    @Value.Default
    default Map<String, JsonNode> arguments() {
        return Map.of();
    }

    @Nullable
    Map<String, JsonNode> meta();

    @Nullable
    Object progressToken();

    @Nullable
    Cancellation cancellation();

    @Nullable
    Map<String, JsonNode> inputResponses();

    @Nullable
    String requestState();

    static DefaultToolRequest.Builder builder() {
        return DefaultToolRequest.builder();
    }

    static ToolRequest of(
            String name,
            @Nullable Map<String, JsonNode> arguments,
            @Nullable Map<String, JsonNode> meta,
            @Nullable Object progressToken,
            @Nullable Cancellation cancellation) {
        return DefaultToolRequest.of(
                name,
                arguments != null ? arguments : Collections.emptyMap(),
                meta,
                progressToken,
                cancellation,
                null,
                null);
    }

    static ToolRequest of(
            String name, @Nullable Map<String, JsonNode> arguments, @Nullable Map<String, JsonNode> meta) {
        return DefaultToolRequest.of(
                name, arguments != null ? arguments : Collections.emptyMap(), meta, null, null, null, null);
    }
}
