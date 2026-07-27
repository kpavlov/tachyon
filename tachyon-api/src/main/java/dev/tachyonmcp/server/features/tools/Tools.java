/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.tools;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Registry for server tools. */
public interface Tools {

    /**
     * Registers a tool descriptor with a synchronous function.
     *
     * @param descriptor the tool descriptor
     * @param fn the tool function
     * @return this registry
     */
    Tools register(ToolDescriptor descriptor, ToolFn fn);

    /**
     * Builds and registers a tool descriptor with a synchronous function.
     *
     * @param configurer the descriptor builder configurer
     * @param fn the tool function
     * @return this registry
     */
    default Tools register(Consumer<ToolDescriptor.Builder> configurer, ToolFn fn) {
        var builder = ToolDescriptor.builder();
        configurer.accept(builder);
        return register(builder.build(), fn);
    }

    /**
     * Registers a tool descriptor with an asynchronous function.
     *
     * @param descriptor the tool descriptor
     * @param fn the asynchronous tool function
     * @return this registry
     */
    Tools registerAsync(ToolDescriptor descriptor, AsyncToolFn fn);

    /**
     * Builds and registers a tool descriptor with an asynchronous function.
     *
     * @param configurer the descriptor builder configurer
     * @param fn the asynchronous tool function
     * @return this registry
     */
    default Tools registerAsync(Consumer<ToolDescriptor.Builder> configurer, AsyncToolFn fn) {
        var builder = ToolDescriptor.builder();
        configurer.accept(builder);
        return registerAsync(builder.build(), fn);
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
