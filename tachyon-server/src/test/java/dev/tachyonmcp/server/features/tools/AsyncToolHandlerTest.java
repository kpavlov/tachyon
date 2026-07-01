/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.server.session.McpContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class AsyncToolHandlerTest {

    @Test
    void descriptorUsesAllOptionalFields() {
        var handler = new AsyncToolHandler() {
            @Override
            public String name() {
                return "my-tool";
            }

            @Override
            public CompletionStage<? extends ToolResult<?>> handleAsync(McpContext ctx, ToolArgs args) {
                return CompletableFuture.completedFuture(ToolResult.text("ok"));
            }
        };
        var desc = handler.descriptor();
        assertThat(desc.name()).isEqualTo("my-tool");
        assertThat(desc.title()).isNull();
        assertThat(desc.description()).isNull();
        assertThat(desc.inputSchema()).isNull();
        assertThat(desc.outputSchema()).isNull();
        assertThat(desc.taskSupport()).isNull();
        assertThat(desc.annotations()).isNull();
    }

    @Test
    void handleDelegatesToHandleAsync() throws Exception {
        var handler = new AsyncToolHandler() {
            @Override
            public String name() {
                return "t";
            }

            @Override
            public CompletionStage<? extends ToolResult<?>> handleAsync(McpContext ctx, ToolArgs args) {
                return CompletableFuture.completedFuture(ToolResult.text("async"));
            }
        };
        var request = ToolRequest.builder().name("t").build();
        var result = handler.handle(request, null).toCompletableFuture().join();
        assertThat(result).isInstanceOf(ToolResult.Success.class);
    }

    @Test
    void adaptWrapsSyncHandler() {
        var sync = SyncToolHandler.of("sync-tool", null, null, (ctx, args) -> ToolResult.text("sync"));
        var async = AsyncToolHandler.adapt(sync);
        assertThat(async.name()).isEqualTo("sync-tool");
        assertThat(async.descriptor().name()).isEqualTo("sync-tool");
    }

    @Test
    void adaptRejectsNull() {
        assertThatThrownBy(() -> AsyncToolHandler.adapt(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sync");
    }

    @Test
    void adaptHandleAsyncReturnsSyncResult() throws Exception {
        var sync = SyncToolHandler.of("t", null, null, (ctx, args) -> ToolResult.text("sync-result"));
        var async = AsyncToolHandler.adapt(sync);
        var result =
                async.handleAsync(null, ToolArgs.of(null)).toCompletableFuture().join();
        assertThat(result).isInstanceOf(ToolResult.Success.class);
    }

    @Test
    void adaptHandleAsyncWrapsSyncException() {
        var sync = SyncToolHandler.of("t", null, null, (ctx, args) -> {
            throw new IllegalArgumentException("sync fail");
        });
        var async = AsyncToolHandler.adapt(sync);
        assertThatThrownBy(() -> async.handleAsync(null, ToolArgs.of(null))
                        .toCompletableFuture()
                        .join())
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasStackTraceContaining("sync fail");
    }

    @Test
    void defaultTitleIsNull() {
        var handler = new AsyncToolHandler() {
            @Override
            public String name() {
                return "t";
            }

            @Override
            public CompletionStage<? extends ToolResult<?>> handleAsync(McpContext ctx, ToolArgs args) {
                return CompletableFuture.completedFuture(ToolResult.text("ok"));
            }
        };
        assertThat(handler.title()).isNull();
    }

    @Test
    void defaultDescriptionIsNull() {
        var handler = new AsyncToolHandler() {
            @Override
            public String name() {
                return "t";
            }

            @Override
            public CompletionStage<? extends ToolResult<?>> handleAsync(McpContext ctx, ToolArgs args) {
                return CompletableFuture.completedFuture(ToolResult.text("ok"));
            }
        };
        assertThat(handler.description()).isNull();
    }

    @Test
    void defaultInputSchemaIsNull() {
        var handler = new AsyncToolHandler() {
            @Override
            public String name() {
                return "t";
            }

            @Override
            public CompletionStage<? extends ToolResult<?>> handleAsync(McpContext ctx, ToolArgs args) {
                return CompletableFuture.completedFuture(ToolResult.text("ok"));
            }
        };
        assertThat(handler.inputSchema()).isNull();
    }

    @Test
    void defaultOutputSchemaIsNull() {
        var handler = new AsyncToolHandler() {
            @Override
            public String name() {
                return "t";
            }

            @Override
            public CompletionStage<? extends ToolResult<?>> handleAsync(McpContext ctx, ToolArgs args) {
                return CompletableFuture.completedFuture(ToolResult.text("ok"));
            }
        };
        assertThat(handler.outputSchema()).isNull();
    }

    @Test
    void defaultTaskSupportIsNull() {
        var handler = new AsyncToolHandler() {
            @Override
            public String name() {
                return "t";
            }

            @Override
            public CompletionStage<? extends ToolResult<?>> handleAsync(McpContext ctx, ToolArgs args) {
                return CompletableFuture.completedFuture(ToolResult.text("ok"));
            }
        };
        assertThat(handler.taskSupport()).isNull();
    }

    @Test
    void defaultAnnotationsIsNull() {
        var handler = new AsyncToolHandler() {
            @Override
            public String name() {
                return "t";
            }

            @Override
            public CompletionStage<? extends ToolResult<?>> handleAsync(McpContext ctx, ToolArgs args) {
                return CompletableFuture.completedFuture(ToolResult.text("ok"));
            }
        };
        assertThat(handler.annotations()).isNull();
    }
}
