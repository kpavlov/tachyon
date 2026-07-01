/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.domain.ContentBlock;
import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.InputRequestBundle;
import dev.tachyonmcp.server.domain.TextContent;
import java.util.*;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public sealed interface ToolResult<T>
        permits ToolResult.Success, ToolResult.ErrorResult, ToolResult.WithMeta, ToolResult.InputRequired {

    record Success<T>(@Nullable T structuredValue, List<ContentBlock> content) implements ToolResult<T> {
        public Success {
            Objects.requireNonNull(content, "content");
            content = List.copyOf(content);
        }

        public Optional<T> structured() {
            return Optional.ofNullable(structuredValue);
        }
    }

    record ErrorResult(String message) implements ToolResult<Object> {}

    record WithMeta<T>(ToolResult<T> inner, Map<String, JsonNode> meta) implements ToolResult<T> {
        public WithMeta {
            Objects.requireNonNull(inner, "inner");
            Objects.requireNonNull(meta, "meta");
            meta = Map.copyOf(meta);
        }
    }

    record InputRequired(InputRequestBundle request) implements ToolResult<Object> {
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

    default ToolResult<T> withMeta(Map<String, JsonNode> m) {
        if (m.isEmpty()) return this;
        if (this instanceof WithMeta<T>(ToolResult<T> inner, Map<String, JsonNode> meta)) {
            var merged = new HashMap<>(meta);
            merged.putAll(m);
            return new WithMeta<>(inner, merged);
        }
        return new WithMeta<>(this, m);
    }

    default ToolResult<T> withMeta(String key, JsonNode value) {
        return withMeta(Map.of(key, value));
    }

    static ToolResult<String> text(String t) {
        return new Success<>(null, List.of(TextContent.of(t)));
    }

    static ToolResult<Void> blocks(ContentBlock... blocks) {
        return new Success<>(null, List.of(blocks));
    }

    static <T> ToolResult<T> of(T payload, String text) {
        return new Success<>(payload, List.of(TextContent.of(text)));
    }

    static <T> ToolResult<T> of(T payload) {
        return new Success<>(payload, List.of(TextContent.of(Objects.toString(payload))));
    }

    static ToolResult<Object> error(String message) {
        return new ErrorResult(message);
    }

    static ToolResult<Object> empty() {
        return new Success<>(null, List.of());
    }

    static ToolResult<Object> inputRequired(Map<String, ? extends InputRequest> reqs, @Nullable String state) {
        return new InputRequired(new InputRequestBundle(reqs, state));
    }
}
