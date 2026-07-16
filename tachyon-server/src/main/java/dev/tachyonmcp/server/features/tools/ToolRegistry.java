/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.Args;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public interface ToolRegistry {

    ToolRegistry register(ToolHandler handler);

    default ToolRegistry register(ToolDescriptor descriptor, BiFunction<InteractionContext, Args, ToolResult> handler) {
        return register(ToolHandler.of(descriptor, handler));
    }

    default ToolRegistry register(
            Consumer<ToolDescriptor.Builder> descriptor, BiFunction<InteractionContext, Args, ToolResult> handler) {
        final var builder = ToolDescriptor.builder();
        descriptor.accept(builder);
        return register(builder.build(), handler);
    }

    default ToolRegistry registerAsync(
            ToolDescriptor descriptor, BiFunction<InteractionContext, Args, CompletionStage<ToolResult>> handler) {
        return register(ToolHandler.ofAsync(descriptor, handler));
    }

    default ToolRegistry registerAsync(
            Consumer<ToolDescriptor.Builder> descriptor,
            BiFunction<InteractionContext, Args, CompletionStage<ToolResult>> handler) {
        final var builder = ToolDescriptor.builder();
        descriptor.accept(builder);
        return registerAsync(builder.build(), handler);
    }

    default ToolRegistry register(
            String name,
            @Nullable String description,
            @Nullable String inputSchemaJson,
            @Nullable String outputSchemaJson,
            BiFunction<InteractionContext, Args, ToolResult> fn) {
        return register(ToolHandler.of(
                builder -> builder.name(name)
                        .description(description)
                        .inputSchema(inputSchemaJson)
                        .outputSchema(outputSchemaJson),
                fn));
    }

    boolean unregister(String name);

    Optional<ToolDescriptor> find(String name);

    List<ToolDescriptor> descriptors();
}
