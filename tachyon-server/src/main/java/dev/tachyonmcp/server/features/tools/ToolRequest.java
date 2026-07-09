/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.Cancellation;
import dev.tachyonmcp.server.domain.HasMeta;
import dev.tachyonmcp.server.json.PayloadDeserializer;
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

    /** Returns the payload deserializer configured for this request, or {@code null} if not set. */
    @Nullable
    PayloadDeserializer payloadDeserializer();

    @Nullable
    Object progressToken();

    @Nullable
    Cancellation cancellation();

    @Nullable
    Map<String, JsonNode> inputResponses();

    @Nullable
    String requestState();

    static Builder builder() {
        return DefaultToolRequest.builder();
    }

    interface Builder {
        Builder name(String name);

        Builder arguments(Map<String, ? extends JsonNode> arguments);

        Builder meta(@Nullable Map<String, ? extends JsonNode> entries);

        Builder payloadDeserializer(@Nullable PayloadDeserializer deserializer);

        Builder progressToken(@Nullable Object progressToken);

        Builder cancellation(@Nullable Cancellation cancellation);

        Builder inputResponses(@Nullable Map<String, ? extends JsonNode> inputResponses);

        Builder requestState(@Nullable String requestState);

        ToolRequest build();
    }
}
