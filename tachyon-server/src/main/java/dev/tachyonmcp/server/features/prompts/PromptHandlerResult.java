/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.PromptMessage;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public sealed interface PromptHandlerResult permits PromptHandlerResult.Messages, PromptHandlerResult.InputRequired {

    record Messages(@Nullable List<PromptMessage> messages) implements PromptHandlerResult {}

    record InputRequired(
            Map<String, ? extends InputRequest> inputRequests,
            @Nullable String requestState) implements PromptHandlerResult {}

    static PromptHandlerResult messages(@Nullable List<PromptMessage> messages) {
        return new Messages(messages);
    }

    static PromptHandlerResult inputRequired(
            Map<String, ? extends InputRequest> inputRequests, @Nullable String requestState) {
        return new InputRequired(inputRequests, requestState);
    }
}
