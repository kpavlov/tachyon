/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.annotations.ExperimentalApi;
import dev.tachyonmcp.runtime.Cancellation;
import dev.tachyonmcp.server.domain.Args;
import dev.tachyonmcp.server.domain.HasMeta;
import dev.tachyonmcp.server.domain.ProgressToken;
import dev.tachyonmcp.server.domain.Task;
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

    /**
     * Tool name
     */
    String name();

    @Value.Check
    default void check() {
        if (name().isBlank()) throw new IllegalArgumentException("name must not be blank");
    }

    @Value.Default
    default Args arguments() {
        return Args.empty();
    }

    @Nullable
    Map<String, JsonNode> meta();

    /**
     * Returns the payload deserializer configured for this request, or {@code null} if not set.
     *
     * <p>Superseded by {@link #arguments()}, which already carries the deserializer; this direct
     * accessor may be removed once callers migrate off it.
     */
    @ExperimentalApi
    @Nullable
    PayloadDeserializer payloadDeserializer();

    /**
     * The client's {@code _meta.progressToken} from this request, or {@code null} if the client
     * did not opt into progress notifications for this call.
     */
    @Nullable
    ProgressToken progressToken();

    @Nullable
    Cancellation cancellation();

    @Nullable
    Map<String, JsonNode> inputResponses();

    @Nullable
    String requestState();

    /**
     * The task handle for task-augmented tool calls, or {@code null} for non-augmented calls.
     */
    @Nullable
    Task task();

    static Builder builder() {
        return DefaultToolRequest.builder();
    }

    interface Builder {
        Builder name(String name);

        Builder arguments(Args arguments);

        Builder meta(@Nullable Map<String, ? extends JsonNode> entries);

        @ExperimentalApi
        Builder payloadDeserializer(@Nullable PayloadDeserializer deserializer);

        /** Sets the progress token, or {@code null} to leave progress notifications disabled. */
        Builder progressToken(@Nullable ProgressToken progressToken);

        Builder cancellation(@Nullable Cancellation cancellation);

        Builder inputResponses(@Nullable Map<String, ? extends JsonNode> inputResponses);

        Builder requestState(@Nullable String requestState);

        Builder task(@Nullable Task task);

        ToolRequest build();
    }
}
