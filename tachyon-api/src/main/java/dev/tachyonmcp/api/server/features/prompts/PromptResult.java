/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.prompts;

import dev.tachyonmcp.api.server.domain.HasMeta;
import dev.tachyonmcp.api.server.domain.InputRequest;
import dev.tachyonmcp.api.server.domain.InputRequestBundle;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Result of a prompt function invocation.
 * <p>
 * A function returns either a list of messages or a request for additional input.
 */
public sealed interface PromptResult extends HasMeta
        permits PromptResult.Messages, PromptResult.InputRequired, PromptResult.WithMeta {

    /** Returns no metadata unless this result is wrapped with {@link WithMeta}. */
    @Override
    default @Nullable Map<String, Object> meta() {
        return null;
    }

    /**
     * A prompt result containing one or more prompt messages.
     *
     * @param messages the prompt messages, or {@code null} for an empty response
     */
    record Messages(@Nullable List<PromptMessage> messages) implements PromptResult {
        public Messages {
            messages = messages == null ? null : List.copyOf(messages);
        }
    }

    /**
     * A prompt result that requests additional input from the client.
     *
     * @param request the input request bundle
     */
    record InputRequired(InputRequestBundle request) implements PromptResult {
        public InputRequired {
            Objects.requireNonNull(request, "request");
        }

        /** Returns the input requests keyed by field name. */
        public Map<String, ? extends InputRequest> inputRequests() {
            return request.inputRequests();
        }

        /** Returns the optional opaque state for resuming this prompt. */
        public @Nullable String requestState() {
            return request.requestState();
        }
    }

    /**
     * Wraps another prompt result with protocol extension metadata.
     *
     * @param inner the wrapped result
     * @param meta the metadata entries
     */
    record WithMeta(PromptResult inner, Map<String, Object> meta) implements PromptResult {
        public WithMeta {
            Objects.requireNonNull(inner, "inner");
            Objects.requireNonNull(meta, "meta");
            meta = Map.copyOf(meta);
        }
    }

    /**
     * Returns this result with the supplied metadata merged into its {@code _meta} entries.
     *
     * @param entries metadata entries to merge
     * @return a result carrying the merged metadata
     */
    default PromptResult withMeta(Map<String, Object> entries) {
        if (entries.isEmpty()) return this;
        if (this instanceof WithMeta(PromptResult inner, Map<String, Object> current)) {
            var merged = new HashMap<>(current);
            merged.putAll(entries);
            return new WithMeta(inner, merged);
        }
        return new WithMeta(this, entries);
    }

    /**
     * Returns this result with one metadata entry.
     *
     * @param key metadata key
     * @param value metadata value
     * @return a result carrying the metadata
     */
    default PromptResult withMeta(String key, Object value) {
        return withMeta(Map.of(key, value));
    }

    /**
     * Creates a prompt result with the given messages.
     *
     * @param messages the prompt messages, or {@code null}
     * @return the prompt result
     */
    static PromptResult messages(@Nullable List<PromptMessage> messages) {
        return new Messages(messages);
    }

    /**
     * Creates a prompt result that requests additional input.
     *
     * @param inputRequests the input requests keyed by field name
     * @param requestState  optional opaque state for resumption
     * @return the prompt result
     */
    static PromptResult inputRequired(
            Map<String, ? extends InputRequest> inputRequests, @Nullable String requestState) {
        return new InputRequired(new InputRequestBundle(inputRequests, requestState));
    }
}
