/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tools;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.json.JsonSchema;
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
     * Registers a tool with a synchronous function operating on decoded, typed arguments.
     *
     * <p>{@code request.arguments()} is decoded into {@code inputType} before {@code fn} runs, and
     * {@code fn}'s return value is wrapped via {@code ToolResult.structured(Object)}. If {@code
     * descriptor} is missing {@link ToolDescriptor#inputSchema()} and/or {@link
     * ToolDescriptor#outputSchema()}, they are filled in via {@link JsonSchema#generated(Class)}
     * for {@code inputType}/{@code outputType} respectively.
     *
     * @param inputType  the type arguments are decoded into
     * @param outputType the result type
     * @param descriptor the tool descriptor
     * @param fn the typed tool function
     * @return this registry
     */
    @ExperimentalApi
    default <I, O> Tools register(
            Class<I> inputType, Class<O> outputType, ToolDescriptor descriptor, TypedToolFn<I, O> fn) {
        return register(
                withGeneratedSchemas(descriptor, inputType, outputType),
                (ctx, request) ->
                        ToolResult.structured(fn.apply(ctx, request.arguments().decode(inputType))));
    }

    /**
     * Builds and registers a tool with a synchronous function operating on decoded, typed
     * arguments. See {@link #register(Class, Class, ToolDescriptor, TypedToolFn)}.
     *
     * @param inputType  the type arguments are decoded into
     * @param outputType the result type
     * @param configurer the descriptor builder configurer
     * @param fn the typed tool function
     * @return this registry
     */
    @ExperimentalApi
    default <I, O> Tools register(
            Class<I> inputType,
            Class<O> outputType,
            Consumer<ToolDescriptor.Builder> configurer,
            TypedToolFn<I, O> fn) {
        var builder = ToolDescriptor.builder();
        configurer.accept(builder);
        return register(inputType, outputType, builder.build(), fn);
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
     * Registers a tool with an asynchronous function operating on decoded, typed arguments. See
     * {@link #register(Class, Class, ToolDescriptor, TypedToolFn)}.
     *
     * @param inputType  the type arguments are decoded into
     * @param outputType the result type
     * @param descriptor the tool descriptor
     * @param fn the typed asynchronous tool function
     * @return this registry
     */
    @ExperimentalApi
    default <I, O> Tools registerAsync(
            Class<I> inputType, Class<O> outputType, ToolDescriptor descriptor, AsyncTypedToolFn<I, O> fn) {
        return registerAsync(
                withGeneratedSchemas(descriptor, inputType, outputType),
                (ctx, request) ->
                        fn.apply(ctx, request.arguments().decode(inputType)).thenApply(ToolResult::structured));
    }

    /**
     * Builds and registers a tool with an asynchronous function operating on decoded, typed
     * arguments. See {@link #register(Class, Class, ToolDescriptor, TypedToolFn)}.
     *
     * @param inputType  the type arguments are decoded into
     * @param outputType the result type
     * @param configurer the descriptor builder configurer
     * @param fn the typed asynchronous tool function
     * @return this registry
     */
    @ExperimentalApi
    default <I, O> Tools registerAsync(
            Class<I> inputType,
            Class<O> outputType,
            Consumer<ToolDescriptor.Builder> configurer,
            AsyncTypedToolFn<I, O> fn) {
        var builder = ToolDescriptor.builder();
        configurer.accept(builder);
        return registerAsync(inputType, outputType, builder.build(), fn);
    }

    /** Fills in {@code descriptor}'s schemas from {@code inputType}/{@code outputType} if absent. */
    private static ToolDescriptor withGeneratedSchemas(
            ToolDescriptor descriptor, Class<?> inputType, Class<?> outputType) {
        if (descriptor.inputSchema() != null && descriptor.outputSchema() != null) {
            return descriptor;
        }
        var builder = ToolDescriptor.builder().from(descriptor);
        if (descriptor.inputSchema() == null) builder.inputSchema(JsonSchema.generated(inputType));
        if (descriptor.outputSchema() == null) builder.outputSchema(JsonSchema.generated(outputType));
        return builder.build();
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
