/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tools;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.server.domain.ContentBlock;
import dev.tachyonmcp.api.server.domain.InputRequest;
import dev.tachyonmcp.api.server.domain.InputRequestBundle;
import dev.tachyonmcp.api.server.domain.TextContent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;
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
     * A failed invocation carrying the error's content blocks.
     */
    @Value.Immutable
    @Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE, typeImmutable = "Default*")
    non-sealed interface Error extends ToolResult {

        /**
         * Returns the error content blocks.
         *
         * @return the error content blocks
         */
        List<ContentBlock> content();

        /**
         * Creates a new builder for constructing {@code Error} instances.
         *
         * @return a new builder
         */
        static Builder builder() {
            return DefaultError.builder();
        }

        /**
         * Creates a failed result carrying the given content blocks.
         *
         * @param content the error content blocks
         * @return a new error
         */
        static Error of(List<? extends ContentBlock> content) {
            return DefaultError.builder().content(content).build();
        }

        /**
         * Builder for {@link Error}.
         */
        interface Builder {
            /**
             * Sets the error content blocks.
             *
             * @param content the error content blocks
             * @return this builder
             */
            Builder content(Iterable<? extends ContentBlock> content);

            /**
             * Builds the {@code Error} instance.
             *
             * @return a new error
             */
            Error build();
        }
    }

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
     * Creates a success result carrying {@code payload} as structured content plus an explicit
     * human-readable text block.
     *
     * @param payload the structured payload
     * @param text    the text content for the content block
     * @return a successful tool result
     */
    static <T> ToolResult structured(T payload, String text) {
        return new Success(payload, List.of(TextContent.of(text)));
    }

    /**
     * Creates a success result carrying {@code payload} as structured content plus an explicit
     * human-readable text block.
     *
     * @param payload the structured payload
     * @param text    the text content for the content block
     * @return a successful tool result
     * @deprecated Use {@link #structured(Object, String)}
     */
    @Deprecated(forRemoval = true)
    static <T> ToolResult of(T payload, String text) {
        return structured(payload, text);
    }

    /**
     * Creates a success result carrying {@code payload} as structured content with no text block.
     *
     * <p>The server emits the serialized JSON of {@code payload} as the text content at encode
     * time (MCP backwards-compat for structured results). Use {@link #structured(Object, String)}
     * to supply an explicit human-readable text block instead.
     *
     * @param payload the structured payload
     * @return a successful tool result
     */
    static <T> ToolResult structured(T payload) {
        return new Success(payload, List.of());
    }

    /**
     * Creates a success result carrying {@code payload} as structured content with no text block.
     *
     * <p>The server emits the serialized JSON of {@code payload} as the text content at encode
     * time (MCP backwards-compat for structured results). Use {@link #structured(Object, String)} to
     * supply an explicit human-readable text block instead.
     *
     * @deprecated Use {@link #structured(Object)}
     */
    @Deprecated(forRemoval = true)
    static ToolResult of(Object payload) {
        return structured(payload);
    }

    /**
     * Creates a failed result with a single text content block carrying the given error message.
     *
     * @param message the error message
     * @return a failed tool result
     */
    static ToolResult error(String message) {
        return Error.of(List.of(TextContent.of(message)));
    }

    /**
     * Creates a failed result carrying the given content blocks.
     *
     * @param blocks the error content blocks
     * @return a failed tool result
     */
    static ToolResult error(ContentBlock... blocks) {
        return Error.of(List.of(blocks));
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
