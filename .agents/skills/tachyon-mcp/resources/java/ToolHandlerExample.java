/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
import dev.tachyonmcp.server.features.tools.SyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Demonstrates the two tool-handler patterns:
 * 1. SyncToolHandler.of() — inline lambda
 * 2. AbstractSyncToolHandler — reusable class
 * 3. LongRunningTool — keep-alive for tools slower than readerIdleTimeout
 */
final class ToolHandlerExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNode GREET_SCHEMA = MAPPER.createObjectNode()
        .put("type", "object")
        .set(
            "properties",
            MAPPER.createObjectNode()
                .set(
                    "name",
                    MAPPER.createObjectNode()
                        .put("type", "string")
                        .put("description", "Name to greet")))
        .set("required", MAPPER.createArrayNode().add("name"));

    /**
     * Lambda-style: use SyncToolHandler.of() inside a builder chain.
     */
    static SyncToolHandler lambdaHello() {
        return SyncToolHandler.of("hello", "Say hello", null, (ctx, args) -> ToolResult.text("Hello, world!"));
    }

    /**
     * Lambda with input schema and arguments.
     */
    static SyncToolHandler lambdaGreet() {
        return SyncToolHandler.of(
            "greeting",
            "Generates a personalized greeting",
            GREET_SCHEMA,
            (@NonNull InteractionContext ctx, ToolArgs args) -> {
                String name = args.string("name");
                return ToolResult.text("Hello, " + name + "!");
            });
    }

    /**
     * Class-based: extend AbstractSyncToolHandler.
     */
    static final class GreetingTool extends AbstractSyncToolHandler {
        GreetingTool() {
            super(builder -> builder
                .name("greeting")
                .title("Greeting")
                .description("Generates a personalized greeting")
                .inputSchema(GREET_SCHEMA)
            );
        }

        @Override
        @NonNull
        public ToolResult handle(InteractionContext ctx, ToolArgs args) {
            var name = args.string("name");
            return ToolResult.text("Hello, " + name + "!");
        }
    }

    /**
     * Keep-alive for a long-running tool.
     *
     * <p>{@code readerIdleTimeout} (default 60s) closes a connection that receives no INBOUND
     * bytes for its duration. A client awaiting a reply sends none, so a tool that computes
     * longer than the timeout is reaped mid-flight. Do NOT just raise the timeout — instead emit
     * an early server→client message: it upgrades the POST response to SSE, after which a
     * scheduler sends {@code :} heartbeats every {@code heartbeatInterval} (15s), keeping the
     * connection alive for the whole run.
     *
     * <p>Two triggers, both reachable only from the request-level {@link ToolHandler} (the
     * {@link ToolArgs} convenience overload carries neither):
     *
     * <ul>
     *   <li>{@code progress(token, ...)} — when the client requested progress, forward its
     *       {@link ToolRequest#progressToken()}. A {@code null} token throws, so guard on it.
     *   <li>{@code comment(msg)} — a token-free SSE comment ({@code : msg}); use it to keep alive
     *       when no progress token is available.
     * </ul>
     */
    static final class LongRunningTool implements ToolHandler {
        private static final int TOTAL = 10;

        @Override
        public ToolDescriptor descriptor() {
            return ToolDescriptor.builder()
                .name("slow-task")
                .description("Long-running task that stays alive via SSE heartbeats")
                .build();
        }

        @Override
        @NonNull
        public ToolResult handle(InteractionContext ctx, ToolRequest request) throws Exception {
            var token = request.progressToken(); // client _meta.progressToken; null if not requested
            for (int i = 0; i < TOTAL; i++) {
                // First call upgrades the POST to SSE and arms the heartbeat. Token present ->
                // progress notification; no token -> raw SSE comment (still keeps the stream alive).
                if (token != null) {
                    ctx.notifications().progress(token, i, TOTAL, "step " + i);
                } else {
                    ctx.notifications().comment("step " + i);
                }
                Thread.sleep(1_000); // blocking I/O is fine on the virtual thread
            }
            return ToolResult.text("done");
        }
    }
}
