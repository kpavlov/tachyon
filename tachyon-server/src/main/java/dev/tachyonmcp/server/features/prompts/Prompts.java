/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.prompts;

import dev.tachyonmcp.annotations.ExperimentalApi;
import dev.tachyonmcp.server.domain.PromptMessage;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Façade interface for MCP prompts
 */
public interface Prompts {
    /**
     * Registers a prompt descriptor with its synchronous handler.
     *
     * @param descriptor the prompt descriptor to register
     * @param handler    the handler invoked for requests matching the prompt
     * @return this registry, or the registry instance resulting from registration
     */
    Prompts register(PromptDescriptor descriptor, PromptHandler handler);

    /**
     * Registers a prompt configured through a descriptor builder.
     *
     * @param configurer configures the prompt descriptor
     * @param handler    handles requests for the registered prompt
     * @return the prompt registry
     */
    default Prompts register(Consumer<PromptDescriptor.Builder> configurer, PromptHandler handler) {
        final var builder = PromptDescriptor.builder();
        configurer.accept(builder);
        return register(builder.build(), handler);
    }

    /**
     * Registers a prompt with an asynchronous handler.
     *
     * @param descriptor the prompt descriptor
     * @param handler    the asynchronous prompt handler
     * @return the prompt registry
     */
    default Prompts registerAsync(PromptDescriptor descriptor, AsyncPromptHandler handler) {
        return register(descriptor, handler);
    }

    /**
     * Registers an asynchronous prompt configured through a descriptor builder.
     *
     * @param descriptor the consumer that configures the prompt descriptor
     * @param handler    the asynchronous handler for the prompt
     * @return the prompt registry
     */
    default Prompts registerAsync(Consumer<PromptDescriptor.Builder> descriptor, AsyncPromptHandler handler) {
        final var builder = PromptDescriptor.builder();
        descriptor.accept(builder);
        return registerAsync(builder.build(), handler);
    }

    /**
     * Registers a prompt that returns the specified messages for every request.
     *
     * @param descriptor the descriptor for the prompt
     * @param messages   the messages returned by the prompt
     * @return           this prompt registry
     */
    @ExperimentalApi
    default Prompts register(PromptDescriptor descriptor, List<PromptMessage> messages) {
        return register(descriptor, (ctx, request) -> PromptResult.messages(messages));
    }

    /**
     * Registers a prompt with a descriptor configured by the supplied consumer and a fixed list of messages.
     *
     * @param descriptor a consumer that configures the prompt descriptor
     * @param messages   the messages returned by the prompt
     * @return this prompt registry
     */
    @ExperimentalApi
    default Prompts register(Consumer<PromptDescriptor.Builder> descriptor, List<PromptMessage> messages) {
        final var builder = PromptDescriptor.builder();
        descriptor.accept(builder);
        return register(builder.build(), messages);
    }

    /**
     * Removes the registered prompt with the specified name.
     *
     * @param name the name of the prompt to remove
     * @return {@code true} if a registration was removed, {@code false} otherwise
     */
    boolean unregister(String name);

    /**
     * Finds a registered prompt descriptor by name.
     *
     * @param name the name of the prompt to find
     * @return an optional containing the matching prompt descriptor, or empty if no prompt is registered with that name
     */
    Optional<PromptDescriptor> find(String name);

    /**
     * Retrieves all registered prompt descriptors.
     *
     * @return a list of registered prompt descriptors
     */
    List<PromptDescriptor> descriptors();
}
