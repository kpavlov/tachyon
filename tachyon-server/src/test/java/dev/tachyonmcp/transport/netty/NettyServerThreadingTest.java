/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.McpDispatcher;
import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
class NettyServerThreadingTest {

    @Test
    void eventLoopsAreOnPlatformThreadsAndToolHandlerOnVirtualThread() throws Exception {
        var handlerThread = new CompletableFuture<String>();

        try (McpServer server = TachyonServer.builder()
                        .tool(new AbstractSyncToolHandler("thread_probe") {

                            @Override
                            public ToolResult handle(McpContext context, ToolArgs args) {
                                Thread thread = Thread.currentThread();
                                handlerThread.complete(thread.getName() + " virtual:" + thread.isVirtual());
                                return ToolResult.empty();
                            }
                        })
                        .build();
                var netty = new NettyServer(0, server)) {
            Callable<Thread> probe = Thread::currentThread;

            Thread workerThread = netty.eventLoopGroup.next().submit(probe).get(10, TimeUnit.SECONDS);
            assertThat(workerThread.isVirtual())
                    .as("worker event loop must run on a platform thread")
                    .isFalse();
            assertThat(workerThread.getName()).startsWith("netty-io-");

            // Drive the tool through the same dispatcher path the Netty handler uses,
            // bypassing the HTTP layer to keep the test focused on threading.
            var session = server.createSession("sess_thread-probe");
            session.activate();
            var dispatcher = new McpDispatcher(server, server.executor());
            var params = java.util.Map.of("name", "thread_probe", "arguments", java.util.Map.of());
            dispatcher
                    .dispatchRequestAsync(1, "tools/call", params, "sess_thread-probe")
                    .join();

            String toolThread = handlerThread.get(10, TimeUnit.SECONDS);
            assertThat(toolThread)
                    .as("tool handler must run on a virtual thread")
                    .endsWith("virtual:true");
        }
    }

    @Test
    void customThreadFactoryAddsNamePrefix() throws Exception {
        var handlerThreadName = new CompletableFuture<String>();

        try (McpServer server = TachyonServer.builder()
                .threadFactory(Thread.ofVirtual().name("tenant-", 0).factory())
                .tool(new AbstractSyncToolHandler("name_probe") {
                    @Override
                    public ToolResult handle(McpContext context, ToolArgs args) {
                        handlerThreadName.complete(Thread.currentThread().getName());
                        return ToolResult.empty();
                    }
                })
                .build()) {
            server.createSession("sess-name").activate();
            var dispatcher = new McpDispatcher(server, server.executor());
            dispatcher
                    .dispatchRequestAsync(
                            1,
                            "tools/call",
                            java.util.Map.of("name", "name_probe", "arguments", java.util.Map.of()),
                            "sess-name")
                    .join();

            String name = handlerThreadName.get(10, TimeUnit.SECONDS);
            assertThat(name).as("thread name must use tenant prefix").startsWith("tenant-");
        }
    }

    @Test
    void callerSuppliedExecutorIsNotShutDownByServerClose() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var closed = new AtomicBoolean(false);

        var server = TachyonServer.builder()
                .executor(executor)
                .tool(new AbstractSyncToolHandler("exec_probe") {
                    @Override
                    public ToolResult handle(McpContext context, ToolArgs args) {
                        return ToolResult.empty();
                    }
                })
                .build();
        server.close();

        // The executor should still be usable (not shut down)
        executor.submit(() -> closed.set(true)).get(5, TimeUnit.SECONDS);
        assertThat(closed)
                .as("caller-owned executor must remain active after server.close()")
                .isTrue();
        executor.shutdown();
    }
}
