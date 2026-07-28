/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.annotations.ExperimentalApi;
import dev.tachyonmcp.json.JsonDocument;
import dev.tachyonmcp.server.domain.ContentBlock;
import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.InputRequestBundle;
import dev.tachyonmcp.server.domain.TextContent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Outcome of a tool invocation: success, error, input-required, or deferred. */
public sealed interface ToolResult
        permits ToolResult.Success,
                ToolResult.Error,
                ToolResult.WithMeta,
                ToolResult.InputRequired,
                ToolResult.Deferred {

    /** Sentinel returned by a task-augmented handler that defers completion to the caller. */
    record Deferred() implements ToolResult {}

    /**
     * A successful invocation, carrying an optional structured value and its content blocks.
     *
     * @param structuredValue the structured payload, or {@code null} when none was set
     * @param content         the content blocks, possibly empty
     */
    record Success(@Nullable Object structuredValue, List<ContentBlock> content) implements ToolResult {
        public Success {
            Objects.requireNonNull(content, "content");
            content = List.copyOf(content);
        }

        /** Returns the structured value, or empty when none was set. */
        public Optional<Object> structured() {
            return Optional.ofNullable(structuredValue);
        }
    }

    /**
     * A failed invocation carrying a human-readable error message.
     *
     * @param message the error message
     */
    record Error(String message) implements ToolResult {}

    /**
     * Wraps another result with request-level {@code _meta} entries.
     *
     * @param inner the wrapped result
     * @param meta  the {@code _meta} entries
     */
    record WithMeta(ToolResult inner, Map<String, Object> meta) implements ToolResult {
        public WithMeta {
            Objects.requireNonNull(inner, "inner");
            Objects.requireNonNull(meta, "meta");
            meta = Map.copyOf(meta);
        }
    }

    /**
     * Signals that completing the tool call requires additional input from the caller.
     *
     * @param request the requested inputs and opaque state to echo back
     */
    record InputRequired(InputRequestBundle request) implements ToolResult {
        public InputRequired {
            Objects.requireNonNull(request, "request");
        }

        /** Returns the requested inputs, keyed by request id. */
        public Map<String, ? extends InputRequest> inputRequests() {
            return request.inputRequests();
        }

        /** Returns the opaque state token to echo back with the caller's response, or {@code null}. */
        public @Nullable String requestState() {
            return request.requestState();
        }
    }

    /**
     * Returns a copy of this result with {@code m} merged into its {@code _meta} entries; returns
     * this unchanged when {@code m} is empty.
     *
     * @param m the {@code _meta} entries to merge in
     * @return a result carrying the merged metadata
     */
    default ToolResult withMeta(Map<String, Object> m) {
        if (m.isEmpty()) return this;
        if (this instanceof WithMeta(ToolResult inner, Map<String, Object> meta)) {
            var merged = new HashMap<>(meta);
            merged.putAll(m);
            return new WithMeta(inner, merged);
        }
        return new WithMeta(this, m);
    }

    /**
     * Returns a copy of this result with a single {@code _meta} entry set.
     *
     * @param key   the {@code _meta} key
     * @param value the {@code _meta} value
     * @return a result carrying the added metadata entry
     */
    default ToolResult withMeta(String key, Object value) {
        return withMeta(Map.of(key, value));
    }

    /**
     * Creates a successful result containing a single text content block.
     *
     * @param t the text
     * @return a successful tool result
     */
    static ToolResult text(String t) {
        return new Success(null, List.of(TextContent.of(t)));
    }

    /**
     * Creates a successful result containing the given content blocks.
     *
     * @param blocks the content blocks
     * @return a successful tool result
     */
    static ToolResult content(ContentBlock... blocks) {
        return new Success(null, List.of(blocks));
    }

    /**
     * Creates a successful result containing the given content blocks.
     *
     * @param blocks the content blocks
     * @return a successful tool result
     * @deprecated use {@link #content(ContentBlock...)}
     */
    @Deprecated(since = "1.0.0-beta.15", forRemoval = true)
    static ToolResult blocks(ContentBlock... blocks) {
        return content(blocks);
    }

    /**
     * Creates a success result carrying {@code payload} as structured content plus an explicit
     * human-readable text block.
     *
     * @param payload the structured payload
     * @param text    the text content for the content block
     * @return a successful tool result
     */
    static ToolResult of(Object payload, String text) {
        return new Success(payload, List.of(TextContent.of(text)));
    }

    /**
     * Alias for {@link #of(Object, String)} with a name that states what the payload becomes
     * (structured content) rather than how it's constructed.
     *
     * @param payload the structured payload
     * @param text    the text content for the content block
     * @return a successful tool result
     */
    @ExperimentalApi
    static ToolResult structured(Object payload, String text) {
        return of(payload, text);
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

    /**
     * Alias for {@link #of(Object)} with a name that states what the payload becomes (structured
     * content) rather than how it's constructed.
     *
     * @param payload the structured payload
     * @return a successful tool result
     */
    @ExperimentalApi
    static ToolResult structured(Object payload) {
        return of(payload);
    }

    /**
     * Creates a failed result with the given error message.
     *
     * @param message the error message
     * @return a failed tool result
     */
    static ToolResult error(String message) {
        return new Error(message);
    }

    /** Creates a successful result with no structured value and no content. */
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
        return new Success(JsonDocument.of(json), List.of(TextContent.of(text)));
    }

    /**
     * Creates a result that requests additional input from the caller before the tool call can
     * complete.
     *
     * @param reqs  the requested inputs, keyed by request id
     * @param state an opaque state token to echo back with the caller's response, or {@code null}
     * @return a result signaling that input is required
     */
    static ToolResult inputRequired(Map<String, ? extends InputRequest> reqs, @Nullable String state) {
        return new InputRequired(new InputRequestBundle(reqs, state));
    }
}
