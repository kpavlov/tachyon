/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.domain.ContentBlock;
import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.InputRequestBundle;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.json.RawJson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public sealed interface ToolResult
        permits ToolResult.Success,
                ToolResult.Error,
                ToolResult.WithMeta,
                ToolResult.InputRequired,
                ToolResult.Deferred {

    /** Sentinel returned by a task-augmented handler that defers completion to the caller. */
    record Deferred() implements ToolResult {}

    record Success(@Nullable Object structuredValue, List<ContentBlock> content) implements ToolResult {
        public Success {
            Objects.requireNonNull(content, "content");
            content = List.copyOf(content);
        }

        public Optional<Object> structured() {
            return Optional.ofNullable(structuredValue);
        }
    }

    record Error(String message) implements ToolResult {}

    record WithMeta(ToolResult inner, Map<String, JsonNode> meta) implements ToolResult {
        public WithMeta {
            Objects.requireNonNull(inner, "inner");
            Objects.requireNonNull(meta, "meta");
            meta = Map.copyOf(meta);
        }
    }

    record InputRequired(InputRequestBundle request) implements ToolResult {
        public InputRequired {
            Objects.requireNonNull(request, "request");
        }

        public Map<String, ? extends InputRequest> inputRequests() {
            return request.inputRequests();
        }

        public @Nullable String requestState() {
            return request.requestState();
        }
    }

    default ToolResult withMeta(Map<String, JsonNode> m) {
        if (m.isEmpty()) return this;
        if (this instanceof WithMeta(ToolResult inner, Map<String, JsonNode> meta)) {
            var merged = new HashMap<>(meta);
            merged.putAll(m);
            return new WithMeta(inner, merged);
        }
        return new WithMeta(this, m);
    }

    default ToolResult withMeta(String key, JsonNode value) {
        return withMeta(Map.of(key, value));
    }

    static ToolResult text(String t) {
        return new Success(null, List.of(TextContent.of(t)));
    }

    static ToolResult blocks(ContentBlock... blocks) {
        return new Success(null, List.of(blocks));
    }

    static ToolResult of(Object payload, String text) {
        return new Success(payload, List.of(TextContent.of(text)));
    }

    /**
     * Creates a success result carrying {@code payload} as structured content with no text block.
     *
     * <p>The server emits the serialized JSON of {@code payload} as the text content at encode
     * time (MCP backwards-compat for structured results). Use {@link #of(Object, String)} to
     * supply an explicit human-readable text block instead.
     */
    static ToolResult of(Object payload) {
        return new Success(payload, List.of());
    }

    static ToolResult error(String message) {
        return new Error(message);
    }

    static ToolResult empty() {
        return new Success(null, List.of());
    }

    /**
     * Creates a success result with a pre-serialized JSON payload. The bytes skip the Jackson
     * value-to-tree conversion of ordinary structured values and are parsed once at envelope
     * encoding (plus once more when output schema validation is active).
     *
     * @param json a pre-serialized JSON object string
     * @param text the text content for the content block
     */
    static ToolResult raw(String json, String text) {
        return new Success(RawJson.of(json), List.of(TextContent.of(text)));
    }

    static ToolResult inputRequired(Map<String, ? extends InputRequest> reqs, @Nullable String state) {
        return new InputRequired(new InputRequestBundle(reqs, state));
    }
}
