/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.session.McpContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public interface SyncToolHandler<I, O> extends ToolHandler {

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

    O handle(McpContext context, I input) throws Exception;

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
    @SuppressWarnings("unchecked")
    default CompletionStage<ToolResult> handle(ToolRequest request, McpContext context) throws Exception {
        var result = handle(context, (I) request.arguments());
        return CompletableFuture.completedFuture(ToolResult.from(result));
    }

    static SyncToolHandler<Object, Object> of(
            String name, @Nullable String description, @Nullable JsonNode inputSchema, ToolFn fn) {
        return new SyncToolHandler<>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public JsonNode inputSchema() {
                return inputSchema;
            }

            @Override
            public Object handle(McpContext ctx, Object args) throws Exception {
                return fn.apply(ctx, args);
            }
        };
    }
}
