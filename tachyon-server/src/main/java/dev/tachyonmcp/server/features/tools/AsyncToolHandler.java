/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.session.McpContext;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public interface AsyncToolHandler<R extends ToolResult> extends ToolHandler<R> {

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

    CompletionStage<R> handleAsync(McpContext context, @Nullable Map<String, JsonNode> arguments) throws Exception;

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
        return handleAsync(context, request.arguments());
    }

    static <R extends ToolResult> AsyncToolHandler<R> adapt(SyncToolHandler<R> sync) {
        Objects.requireNonNull(sync, "sync");
        return new AsyncToolHandler<>() {
            @Override
            public String name() {
                return sync.name();
            }

            @Override
            public ToolDescriptor descriptor() {
                return sync.descriptor();
            }

            @Override
            public CompletionStage<R> handleAsync(McpContext ctx, @Nullable Map<String, JsonNode> input) {
                try {
                    return CompletableFuture.completedFuture(sync.handle(ctx, input));
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }
            }
        };
    }
}
