/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.prompts;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Façade interface for MCP prompts
 */
public interface Prompts {
    /**
     * Registers a prompt descriptor with its synchronous function.
     *
     * @param descriptor the prompt descriptor to register
     * @param fn         the prompt function
     * @return this registry, or the registry instance resulting from registration
     */
    Prompts register(PromptDescriptor descriptor, PromptFn fn);

    /**
     * Registers a prompt configured through a descriptor builder.
     *
     * @param configurer configures the prompt descriptor
     * @param fn         the prompt function
     * @return the prompt registry
     */
    default Prompts register(Consumer<PromptDescriptor.Builder> configurer, PromptFn fn) {
        final var builder = PromptDescriptor.builder();
        configurer.accept(builder);
        return register(builder.build(), fn);
    }

    /**
     * Registers a prompt with an asynchronous function.
     *
     * @param descriptor the prompt descriptor
     * @param fn         the asynchronous prompt function
     * @return the prompt registry
     */
    Prompts registerAsync(PromptDescriptor descriptor, AsyncPromptFn fn);

    /**
     * Registers an asynchronous prompt configured through a descriptor builder.
     *
     * @param descriptor the consumer that configures the prompt descriptor
     * @param fn         the asynchronous function for the prompt
     * @return the prompt registry
     */
    default Prompts registerAsync(Consumer<PromptDescriptor.Builder> descriptor, AsyncPromptFn fn) {
        final var builder = PromptDescriptor.builder();
        descriptor.accept(builder);
        return registerAsync(builder.build(), fn);
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
