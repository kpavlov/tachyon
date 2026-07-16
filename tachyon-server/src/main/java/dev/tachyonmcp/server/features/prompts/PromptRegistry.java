/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.annotations.ExperimentalApi;
import dev.tachyonmcp.server.domain.PromptMessage;
import java.util.List;
import java.util.function.Consumer;

public interface PromptRegistry {
    PromptRegistry add(PromptDescriptor descriptor, PromptHandler handler);

    default PromptRegistry add(Consumer<PromptDescriptor.Builder> configurer, PromptHandler handler) {
        final var builder = PromptDescriptor.builder();
        configurer.accept(builder);
        return add(builder.build(), handler);
    }

    @ExperimentalApi
    default PromptRegistry add(PromptDescriptor descriptor, List<PromptMessage> messages) {
        return add(descriptor, (ctx, request) -> PromptResult.messages(messages));
    }
}
