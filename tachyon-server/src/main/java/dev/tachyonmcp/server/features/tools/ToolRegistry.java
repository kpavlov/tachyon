/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.Args;
import java.util.Collection;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

public interface ToolRegistry {

    ToolRegistry add(ToolHandler handler);

    default ToolRegistry add(
            String name,
            @Nullable String description,
            @Nullable String inputSchemaJson,
            @Nullable String outputSchemaJson,
            BiFunction<InteractionContext, Args, ToolResult> fn) {
        return add(ToolHandler.of(
                builder -> builder.name(name)
                        .description(description)
                        .inputSchema(inputSchemaJson)
                        .outputSchema(outputSchemaJson),
                fn));
    }

    ToolRegistry remove(String name);

    @Nullable
    ToolHandler get(String name);

    Collection<ToolHandler> getAll();

    default @Nullable ToolDescriptor getDescriptor(String name) {
        var handler = get(name);
        return handler != null ? handler.descriptor() : null;
    }
}
