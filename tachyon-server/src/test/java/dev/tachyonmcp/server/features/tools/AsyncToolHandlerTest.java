/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.features.HandlerFutures;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AsyncToolHandlerTest {

    @Test
    void descriptorUsesAllOptionalFields() {
        var handler =
                ToolHandler.ofAsync("my-tool", (ctx, args) -> CompletableFuture.completedStage(ToolResult.text("ok")));

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
    void handleAsyncReturnsResultForArgsOverride() throws Exception {
        var handler =
                ToolHandler.ofAsync("t", (ctx, args) -> CompletableFuture.completedStage(ToolResult.text("async")));
        var request = ToolRequest.builder().name("t").build();
        var result = handler.handleAsync(null, request).toCompletableFuture().get();
        assertThat(result).isInstanceOf(ToolResult.Success.class);
    }

    @Test
    void interruptExitsBlockedHandle() throws InterruptedException {
        var neverCompletes = new CompletableFuture<ToolResult>();
        var handler = ToolHandler.ofAsync("blocking", (ctx, args) -> neverCompletes);
        var interrupted = new AtomicBoolean(false);
        var request = ToolRequest.builder().name("blocking").build();
        var thread = Thread.ofVirtual().start(() -> {
            try {
                HandlerFutures.joinInterruptibly(handler.handleAsync(null, request));
            } catch (Exception e) {
                interrupted.set(true);
            }
        });
        thread.interrupt();
        thread.join(5_000);
        assertThat(interrupted).isTrue();
    }

    @Test
    void defaultsAreNull() {
        var handler = ToolHandler.ofAsync("t", (ctx, args) -> CompletableFuture.completedStage(ToolResult.text("ok")));

        var descriptor = handler.descriptor();
        assertThat(descriptor.name()).isEqualTo("t");
        assertThat(descriptor.title()).isNull();
        assertThat(descriptor.description()).isNull();
        assertThat(descriptor.inputSchema()).isNull();
        assertThat(descriptor.outputSchema()).isNull();
        assertThat(descriptor.taskSupport()).isNull();
        assertThat(descriptor.annotations()).isNull();
    }

    @Test
    void ofAsyncWithDescription() throws Exception {
        var handler = ToolHandler.ofAsync(
                "greeter", "Says hello", (ctx, args) -> CompletableFuture.completedStage(ToolResult.text("Hello!")));

        assertThat(handler.descriptor().name()).isEqualTo("greeter");
        assertThat(handler.descriptor().description()).isEqualTo("Says hello");

        var request = ToolRequest.builder().name("greeter").build();
        var result = handler.handleAsync(null, request).toCompletableFuture().get();
        assertThat(result).isInstanceOf(ToolResult.Success.class);
        assertThat(((ToolResult.Success) result).content())
                .singleElement()
                .satisfies(c -> assertThat(c).isInstanceOf(dev.tachyonmcp.server.domain.TextContent.class));
    }

    @Test
    void ofAsyncRequestReturnsResult() throws Exception {
        var descriptor = ToolDescriptor.builder()
                .name("echo")
                .description("Echo request name")
                .build();
        var handler = ToolHandler.ofAsyncRequest(
                descriptor, (ctx, request) -> CompletableFuture.completedStage(ToolResult.text(request.name())));

        assertThat(handler.descriptor()).isEqualTo(descriptor);

        var request = ToolRequest.builder().name("echo").build();
        var result = handler.handleAsync(null, request).toCompletableFuture().get();
        assertThat(result).isInstanceOf(ToolResult.Success.class);
    }

    @Test
    void ofAsyncRequestForwardsRequestName() throws Exception {
        var descriptor = ToolDescriptor.builder()
                .name("greeter")
                .description("Returns the tool name")
                .build();
        var handler = ToolHandler.ofAsyncRequest(
                descriptor, (ctx, request) -> CompletableFuture.completedStage(ToolResult.text(request.name())));

        var request = ToolRequest.builder().name("greeter").build();
        var result = (ToolResult.Success)
                handler.handleAsync(null, request).toCompletableFuture().get();
        var text = ((dev.tachyonmcp.server.domain.TextContent) result.content().getFirst()).text();
        assertThat(text).isEqualTo("greeter");
    }
}
