/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tools;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public interface Tools {

    /**
     * Registers a tool handler with the registry.
     *
     * @param handler the tool handler to register
     * @return this registry
     */
    Tools register(ToolHandler handler);

    /**
     * Registers a tool descriptor with a synchronous handler.
     *
     * @param descriptor the descriptor for the tool to register
     * @param handler    the function that handles tool interactions, given the raw {@link ToolRequest}
     * @return this tool registry
     */
    default Tools register(ToolDescriptor descriptor, ToolFn handler) {
        return register(ToolHandler.of(descriptor, handler));
    }

    /**
     * Registers a tool using a consumer to configure its descriptor and a synchronous handler.
     *
     * @param descriptor the consumer that configures the tool descriptor
     * @param handler the function that handles tool interactions
     * @return this tool registry
     */
    default Tools register(Consumer<ToolDescriptor.Builder> descriptor, ToolFn handler) {
        final var builder = ToolDescriptor.builder();
        descriptor.accept(builder);
        return register(builder.build(), handler);
    }

    /**
     * Registers a tool with an asynchronous handler.
     *
     * @param descriptor the descriptor for the tool
     * @param handler the function that handles interactions and produces a result asynchronously
     * @return this tool registry
     */
    default Tools registerAsync(ToolDescriptor descriptor, AsyncToolFn handler) {
        return register(ToolHandler.ofAsync(descriptor, handler));
    }

    /**
     * Registers a tool configured by a descriptor builder and handled asynchronously.
     *
     * @param descriptor configures the tool descriptor
     * @param handler    processes interactions and produces the tool result
     * @return this tool registry
     */
    default Tools registerAsync(Consumer<ToolDescriptor.Builder> descriptor, AsyncToolFn handler) {
        final var builder = ToolDescriptor.builder();
        descriptor.accept(builder);
        return registerAsync(builder.build(), handler);
    }

    /**
     * Registers a tool with the specified metadata and synchronous handler.
     *
     * @param name              the tool name
     * @param description       the tool description
     * @param inputSchemaJson   the JSON schema for tool inputs
     * @param outputSchemaJson  the JSON schema for tool outputs
     * @param fn                the function that handles tool interactions
     * @return the registry
     */
    default Tools register(
            String name,
            @Nullable String description,
            @Nullable String inputSchemaJson,
            @Nullable String outputSchemaJson,
            ToolFn fn) {
        return register(ToolHandler.of(
                builder -> builder.name(name)
                        .description(description)
                        .inputSchema(inputSchemaJson)
                        .outputSchema(outputSchemaJson),
                fn));
    }

    /**
     * Removes the registered tool with the specified name.
     *
     * @param name the name of the tool to remove
     * @return {@code true} if a tool was removed, {@code false} if no tool was registered with that name
     */
    boolean unregister(String name);

    /**
     * Finds a registered tool descriptor by name.
     *
     * @param name the name of the tool to find
     * @return the matching tool descriptor, or an empty optional if no tool is registered with that name
     */
    Optional<ToolDescriptor> find(String name);

    /**
     * Lists the descriptors of all registered tools.
     *
     * @return the registered tool descriptors
     */
    List<ToolDescriptor> descriptors();
}
