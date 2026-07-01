/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.session.McpContext;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Convenient base for asynchronous (non-blocking) tool handlers. */
public interface AsyncToolHandler extends ToolHandler {

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

    /** Executes the tool asynchronously with parsed args. */
    CompletionStage<? extends ToolResult<?>> handleAsync(McpContext context, ToolArgs args);

    /** Executes the tool asynchronously with the full request (includes meta/progressToken). */
    default CompletionStage<? extends ToolResult<?>> handleAsync(McpContext context, ToolRequest request) {
        return handleAsync(context, ToolArgs.of(request.arguments()));
    }

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
    default CompletionStage<? extends ToolResult<?>> handle(ToolRequest request, McpContext context) {
        return handleAsync(context, request);
    }

    /** Wraps a synchronous tool handler as an async one. */
    static AsyncToolHandler adapt(SyncToolHandler sync) {
        Objects.requireNonNull(sync, "sync");
        return new AsyncToolHandler() {
            @Override
            public String name() {
                return sync.name();
            }

            @Override
            public ToolDescriptor descriptor() {
                return sync.descriptor();
            }

            @Override
            public CompletionStage<? extends ToolResult<?>> handleAsync(McpContext ctx, ToolArgs args) {
                try {
                    return CompletableFuture.completedFuture(sync.handle(ctx, args));
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }
            }
        };
    }
}
