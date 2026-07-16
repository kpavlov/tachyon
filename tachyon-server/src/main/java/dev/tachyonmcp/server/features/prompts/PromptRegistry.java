/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.annotations.ExperimentalApi;
import dev.tachyonmcp.server.domain.PromptMessage;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface PromptRegistry {
    PromptRegistry register(PromptDescriptor descriptor, PromptHandler handler);

    default PromptRegistry register(Consumer<PromptDescriptor.Builder> configurer, PromptHandler handler) {
        final var builder = PromptDescriptor.builder();
        configurer.accept(builder);
        return register(builder.build(), handler);
    }

    default PromptRegistry registerAsync(PromptDescriptor descriptor, AsyncPromptHandler handler) {
        return register(descriptor, handler);
    }

    default PromptRegistry registerAsync(Consumer<PromptDescriptor.Builder> descriptor, AsyncPromptHandler handler) {
        final var builder = PromptDescriptor.builder();
        descriptor.accept(builder);
        return registerAsync(builder.build(), handler);
    }

    @ExperimentalApi
    default PromptRegistry register(PromptDescriptor descriptor, List<PromptMessage> messages) {
        return register(descriptor, (ctx, request) -> PromptResult.messages(messages));
    }

    @ExperimentalApi
    default PromptRegistry register(Consumer<PromptDescriptor.Builder> descriptor, List<PromptMessage> messages) {
        final var builder = PromptDescriptor.builder();
        descriptor.accept(builder);
        return register(builder.build(), messages);
    }

    boolean unregister(String name);

    Optional<PromptDescriptor> find(String name);

    List<PromptDescriptor> descriptors();
}
