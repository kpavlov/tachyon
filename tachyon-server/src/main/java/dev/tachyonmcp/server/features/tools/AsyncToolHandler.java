/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.McpToolMapper;
import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.session.McpContext;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public interface AsyncToolHandler<I, O> extends ToolHandler {

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

    CompletionStage<O> handleAsync(McpContext context, I input) throws Exception;

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
        return handleAsync(context, (I) request.arguments()).thenApply(McpToolMapper::toDomainResult);
    }

    static <I, O> AsyncToolHandler<I, O> adapt(SyncToolHandler<I, O> sync) {
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
            public CompletionStage<O> handleAsync(McpContext ctx, I input) {
                try {
                    return CompletableFuture.completedFuture(sync.handle(ctx, input));
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }
            }
        };
    }
}
