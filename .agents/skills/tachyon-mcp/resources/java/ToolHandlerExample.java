/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Demonstrates tool-handler patterns:
 * 1. ToolHandler.of() — inline lambda
 * 2. ToolHandler.of() with Consumer&lt;Builder&gt; — reusable config
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
     * Lambda-style: use ToolHandler.of() inside a builder chain.
     */
    static ToolHandler lambdaHello() {
        return ToolHandler.of("hello", "Say hello", (ctx, request) -> ToolResult.text("Hello, world!"));
    }

    /**
     * Lambda with input schema and arguments.
     */
    static ToolHandler lambdaGreet() {
        return ToolHandler.of(
            b -> b.name("greeting")
                .description("Generates a personalized greeting")
                .inputSchema(GREET_SCHEMA),
            (@NonNull InteractionContext ctx, ToolRequest request) -> {
                String name = request.arguments().stringValue("name");
                return ToolResult.text("Hello, " + name + "!");
            });
    }

    /**
     * Reusable ToolHandler via Consumer&lt;Builder&gt;.
     */
    static ToolHandler greetingTool() {
        return ToolHandler.of(b -> b
                .name("greeting")
                .title("Greeting")
                .description("Generates a personalized greeting")
                .inputSchema(GREET_SCHEMA),
            (ctx, request) -> {
                var name = request.arguments().stringValue("name");
                return ToolResult.text("Hello, " + name + "!");
            });
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
     * <p>Two triggers, both reachable off the {@link ToolRequest} every handler receives:
     *
     * <ul>
     *   <li>{@code progress(token, ...)} — when the client requested progress, forward its
     *       {@link ToolRequest#progressToken()}. A {@code null} token is silently dropped per the
     *       MCP spec (the client didn't opt in) — no bytes are sent, so it does NOT keep the
     *       connection alive.
     *   <li>{@code comment(msg)} — a token-free SSE comment ({@code : msg}); use it to keep alive
     *       when no progress token is available, since a dropped {@code progress(null, ...)} sends
     *       nothing.
     * </ul>
     */
    static final class LongRunningTool extends AbstractToolHandler {
        private static final int TOTAL = 10;

        LongRunningTool() {
            super(ToolDescriptor.builder()
                .name("slow-task")
                .description("Long-running task that stays alive via SSE heartbeats")
                .build());
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
