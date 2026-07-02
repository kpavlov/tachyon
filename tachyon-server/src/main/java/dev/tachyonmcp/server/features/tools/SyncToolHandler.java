/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Convenient base for synchronous (blocking) tool handlers. */
public interface SyncToolHandler extends ToolHandler {

    /** The tool name. */
    String name();

    @Nullable
    default String title() {
        return null;
    }

    @Nullable
    default String description() {
        return null;
    }

    @Nullable
    default JsonNode inputSchema() {
        return null;
    }

    @Nullable
    default JsonNode outputSchema() {
        return null;
    }

    @Nullable
    default TaskSupport taskSupport() {
        return null;
    }

    /** Executes the tool synchronously. */
    ToolResult handle(InteractionContext context, ToolArgs args) throws Exception;

    @Nullable
    default ToolAnnotations annotations() {
        return null;
    }

    @Override
    default ToolDescriptor descriptor() {
        return ToolDescriptor.builder(name())
                .title(title())
                .description(description())
                .inputSchema(inputSchema())
                .outputSchema(outputSchema())
                .taskSupport(taskSupport())
                .annotations(annotations())
                .build();
    }

    @Override
    default CompletionStage<? extends ToolResult> handle(InteractionContext context, ToolRequest request) {
        try {
            return CompletableFuture.completedFuture(handle(context, ToolArgs.of(request.arguments())));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Creates a simple SyncToolHandler from a name, description, schema, and function. */
    static SyncToolHandler of(
            String name,
            @Nullable String description,
            @Nullable JsonNode inputSchema,
            BiFunction<InteractionContext, ToolArgs, ToolResult> fn) {
        return new SyncToolHandler() {
            @Override
            public String name() {
                return name;
            }

            @Override
            @Nullable
            public String description() {
                return description;
            }

            @Override
            @Nullable
            public JsonNode inputSchema() {
                return inputSchema;
            }

            @Override
            public ToolResult handle(InteractionContext ctx, ToolArgs args) throws Exception {
                return fn.apply(ctx, args);
            }
        };
    }
}
