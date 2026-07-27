/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.protocol.api.server.features.prompts;

import dev.tachyonmcp.protocol.api.server.domain.InputRequest;
import dev.tachyonmcp.protocol.api.server.domain.InputRequestBundle;
import dev.tachyonmcp.protocol.api.server.domain.PromptMessage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Result of a prompt handler invocation.
 * <p>
 * A handler returns either a list of messages or a request for additional input.
 */
public sealed interface PromptResult permits PromptResult.Messages, PromptResult.InputRequired {

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
