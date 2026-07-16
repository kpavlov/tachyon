/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import dev.tachyonmcp.annotations.ExperimentalApi;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@ExperimentalApi
public sealed interface TaskResult extends HasMeta permits TaskResult.Completed, TaskResult.Failed {

    static Completed completed(
            List<ContentBlock> content, @Nullable JsonNode structuredContent, @Nullable Map<String, JsonNode> meta) {
        return new Completed(content, structuredContent, meta);
    }

    static Completed completed(JsonNode structuredContent) {
        return new Completed(Collections.emptyList(), structuredContent, null);
    }

    static Failed failed(String message) {
        return new Failed(List.of(TextContent.of(message)), null, null);
    }

    record Completed(
            List<ContentBlock> content,
            @Nullable JsonNode structuredContent,
            @Nullable Map<String, JsonNode> meta) implements TaskResult {
        public Completed {
            Objects.requireNonNull(content, "content");
            content = List.copyOf(content);
        }

        public Completed(@Nullable Map<String, JsonNode> meta) {
            this(List.of(), null, meta);
        }
    }

    record Failed(
            List<ContentBlock> content,
            @Nullable JsonNode structuredContent,
            @Nullable Map<String, JsonNode> meta) implements TaskResult {
        public Failed {
            Objects.requireNonNull(content, "content");
            content = List.copyOf(content);
        }

        public Failed(@Nullable Map<String, JsonNode> meta) {
            this(List.of(), null, meta);
        }
    }
}
