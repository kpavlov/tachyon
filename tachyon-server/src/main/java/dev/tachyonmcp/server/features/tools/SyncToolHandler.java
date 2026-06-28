/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.session.McpContext;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public interface SyncToolHandler<R extends ToolResult> extends ToolHandler<R> {

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

    R handle(McpContext context, @Nullable Map<String, JsonNode> arguments) throws Exception;

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
    default CompletionStage<R> handle(ToolRequest request, McpContext context) throws Exception {
        return CompletableFuture.completedFuture(handle(context, request.arguments()));
    }

    static <R extends ToolResult> SyncToolHandler<R> of(
            String name,
            @Nullable String description,
            @Nullable JsonNode inputSchema,
            BiFunction<McpContext, @Nullable Map<String, JsonNode>, R> fn) {
        return new SyncToolHandler<>() {
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
            public R handle(McpContext ctx, @Nullable Map<String, JsonNode> args) throws Exception {
                return fn.apply(ctx, args);
            }
        };
    }
}
