/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.InputRequestBundle;
import dev.tachyonmcp.server.domain.PromptMessage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public sealed interface PromptHandlerResult permits PromptHandlerResult.Messages, PromptHandlerResult.InputRequired {

    record Messages(@Nullable List<PromptMessage> messages) implements PromptHandlerResult {}

    record InputRequired(InputRequestBundle request) implements PromptHandlerResult {
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

    static PromptHandlerResult messages(@Nullable List<PromptMessage> messages) {
        return new Messages(messages);
    }

    static PromptHandlerResult inputRequired(
            Map<String, ? extends InputRequest> inputRequests, @Nullable String requestState) {
        return new InputRequired(new InputRequestBundle(inputRequests, requestState));
    }
}
